"""Local safety tests for the opt-in preflight. Network is always mocked."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import urllib.error
import urllib.request


SCRIPT = Path(__file__).with_name('mercadopago-test-preflight.py')
spec = importlib.util.spec_from_file_location('mercadopago_test_preflight', SCRIPT)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class FakeResponse:
    def __init__(self, body, code=200):
        self.body = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.code = code

    def read(self, limit):
        return self.body[:limit]

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False


class MercadoPagoTestPreflightTests(unittest.TestCase):
    ACCESS = 'TEST-synthetic-access-token'
    PUBLIC = 'TEST-synthetic-public-key'
    CARD_TOKEN = 'synthetic-card-token-never-save'

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='planno-mp-preflight-unit-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.credentials = self.root / 'synthetic.env'
        self.output = self.root / 'summary.json'
        self.write_credentials()

    def write_credentials(self, access=None, public=None, mode=0o600):
        self.credentials.write_text('MP_TEST_ACCESS_TOKEN=' + (self.ACCESS if access is None else access)
                                    + '\nMP_TEST_PUBLIC_KEY=' + (self.PUBLIC if public is None else public) + '\n')
        os.chmod(self.credentials, mode)

    def run_preflight(self, responses=(), tokenize=False):
        opener = mock.Mock()
        opener.open.side_effect = responses
        stream = io.StringIO()
        argv = [str(SCRIPT), '--credentials-file', str(self.credentials), '--output', str(self.output)]
        if tokenize:
            argv.append('--tokenize-test-card')
        with mock.patch('sys.argv', argv), mock.patch.object(preflight.urllib.request, 'build_opener', return_value=opener) as build:
            with contextlib.redirect_stdout(stream):
                preflight.main()
        return opener, build, stream.getvalue(), json.loads(self.output.read_text())

    def assert_blocked_before_network(self):
        with mock.patch('sys.argv', [str(SCRIPT), '--credentials-file', str(self.credentials), '--output', str(self.output)]):
            with mock.patch.object(preflight.urllib.request, 'build_opener') as network:
                with self.assertRaises(SystemExit):
                    preflight.main()
                network.assert_not_called()

    def assert_no_sensitive_output(self, text):
        for value in (self.ACCESS, self.PUBLIC, self.CARD_TOKEN, '4235647728025682', '12345678909'):
            self.assertNotIn(value, text)

    def test_production_access_token_is_rejected_before_network(self):
        self.write_credentials(access='APP_USR-synthetic-production-shaped-token')
        self.assert_blocked_before_network()
        self.assertFalse(self.output.exists())

    def test_production_public_key_is_rejected_before_network(self):
        self.write_credentials(public='APP_USR-synthetic-production-shaped-key')
        self.assert_blocked_before_network()

    def test_missing_test_access_token_is_rejected_before_network(self):
        self.write_credentials(access='')
        self.assert_blocked_before_network()

    def test_missing_test_public_key_is_rejected_before_network(self):
        self.write_credentials(public='')
        self.assert_blocked_before_network()

    def test_malformed_test_credentials_are_rejected_before_network(self):
        self.write_credentials(access='TEST-contains unsafe whitespace')
        self.assert_blocked_before_network()

    def test_group_or_world_readable_credentials_are_rejected_before_network(self):
        self.write_credentials(mode=0o644)
        self.assert_blocked_before_network()

    def test_missing_credentials_file_is_rejected_before_network(self):
        self.credentials.unlink()
        self.assert_blocked_before_network()

    def test_symlink_credentials_are_rejected_before_network(self):
        real = self.root / 'real-synthetic.env'
        self.credentials.rename(real)
        self.credentials.symlink_to(real)
        self.assert_blocked_before_network()

    def test_existing_output_is_preserved_and_rejected_before_network(self):
        self.output.write_text('existing-local-evidence')
        self.assert_blocked_before_network()
        self.assertEqual('existing-local-evidence', self.output.read_text())

    def test_dangling_output_symlink_is_rejected_before_network(self):
        self.output.symlink_to(self.root / 'absent-target')
        self.assert_blocked_before_network()
        self.assertFalse((self.root / 'absent-target').exists())

    def test_default_mode_is_get_only_with_synthetic_reference_and_allowlisted_output(self):
        responses = [FakeResponse([{'id': 'visa', 'private_field': self.CARD_TOKEN}]),
                     FakeResponse({'results': [], 'private_field': self.ACCESS})]
        opener, build, printed, result = self.run_preflight(responses)
        requests = [call.args[0] for call in opener.open.call_args_list]
        self.assertEqual(2, len(requests))
        self.assertTrue(all(request.get_method() == 'GET' and request.data is None for request in requests))
        self.assertEqual('https://api.mercadopago.com/v1/payment_methods', requests[0].full_url)
        self.assertTrue(requests[1].full_url.startswith('https://api.mercadopago.com/v1/payments/search?external_reference=planno-contract-test-'))
        self.assertTrue(all(call.kwargs['timeout'] == 30 for call in opener.open.call_args_list))
        self.assertIsInstance(build.call_args.args[0], preflight.NoRedirect)
        self.assertEqual(0, result['mutating_requests'])
        self.assertEqual(0, result['payment_requests'])
        self.assertFalse(result['raw_responses_saved'])
        self.assertEqual('REAL_HTTP_TEST_CREDENTIALS_READ_ONLY', result['classification'])
        self.assertEqual(0o600, self.output.stat().st_mode & 0o777)
        self.assertTrue(result['observations'][0]['visa_present'])
        self.assertEqual(0, result['observations'][1]['result_count'])
        self.assert_no_sensitive_output(printed + self.output.read_text())

    def test_opt_in_only_tokenizes_fixed_fictitious_card_and_never_saves_or_logs_token(self):
        responses = [FakeResponse([]), FakeResponse({'results': []}),
                     FakeResponse({'id': self.CARD_TOKEN, 'live_mode': False,
                                   'luhn_validation': True, 'status': 'active'}, 201)]
        opener, _, printed, result = self.run_preflight(responses, tokenize=True)
        requests = [call.args[0] for call in opener.open.call_args_list]
        self.assertEqual(['GET', 'GET', 'POST'], [request.get_method() for request in requests])
        self.assertEqual('https://api.mercadopago.com/v1/card_tokens', requests[2].full_url)
        self.assertEqual({'card_number': '4235647728025682', 'security_code': '123',
                          'expiration_month': 11, 'expiration_year': 2030,
                          'cardholder': {'name': 'APRO', 'identification': {'type': 'CPF', 'number': '12345678909'}}},
                         json.loads(requests[2].data))
        self.assertEqual(1, result['mutating_requests'])
        self.assertEqual(0, result['payment_requests'])
        observation = result['observations'][2]
        self.assertTrue(observation['token_id_present'])
        self.assertTrue(observation['live_mode_false'])
        self.assertTrue(observation['luhn_validation_true'])
        self.assertTrue(observation['status_active'])
        self.assertFalse(observation['token_saved_or_used'])
        self.assert_no_sensitive_output(printed + self.output.read_text())

    def test_unexpected_live_mode_is_flagged_and_token_never_saved_or_used(self):
        responses = [FakeResponse([]), FakeResponse({'results': []}),
                     FakeResponse({'id': self.CARD_TOKEN, 'live_mode': True}, 201)]
        opener, _, printed, result = self.run_preflight(responses, tokenize=True)
        self.assertEqual(3, opener.open.call_count)
        observation = result['observations'][2]
        self.assertFalse(observation['live_mode_false'])
        self.assertTrue(observation['unexpected_mode_stop'])
        self.assertFalse(observation['token_saved_or_used'])
        self.assert_no_sensitive_output(printed + self.output.read_text())

    def test_connection_errors_are_summarized_without_raw_exception(self):
        opener, _, printed, result = self.run_preflight([
            urllib.error.URLError(self.ACCESS), TimeoutError(self.CARD_TOKEN)])
        self.assertEqual(2, opener.open.call_count)
        self.assertTrue(all(o['result'] == 'connection_failed' for o in result['observations']))
        self.assertTrue(all(o['authenticated_contract_validated'] is False for o in result['observations']))
        self.assert_no_sensitive_output(printed + self.output.read_text())

    def test_malformed_json_is_not_saved_or_treated_as_a_verified_contract(self):
        _, _, printed, result = self.run_preflight([FakeResponse((self.ACCESS + ' invalid-json').encode()),
                                                   FakeResponse(b'not-json')])
        self.assertTrue(all(o['json_response'] is False for o in result['observations']))
        self.assert_no_sensitive_output(printed + self.output.read_text())

    def test_error_fields_cannot_echo_credentials_or_a_card_token(self):
        _, _, printed, _ = self.run_preflight([
            FakeResponse({'error': self.ACCESS, 'cause': [{'code': self.CARD_TOKEN}]}, 401),
            FakeResponse({'results': []})])
        self.assert_no_sensitive_output(printed + self.output.read_text())

    def test_oversized_response_is_bounded_and_not_parsed(self):
        _, _, printed, result = self.run_preflight([FakeResponse(b' ' * 2_000_002), FakeResponse({'results': []})])
        self.assertFalse(result['observations'][0]['json_response'])
        self.assertLess(len(printed), 3000)

    def test_redirect_handler_refuses_forwarding_authorization(self):
        request = urllib.request.Request('https://api.mercadopago.com/v1/payment_methods',
                                         headers={'Authorization': 'Bearer ' + self.ACCESS})
        handler = preflight.NoRedirect()
        for destination in ('https://other.example.test/collect', 'http://api.mercadopago.com/insecure',
                            'https://api.mercadopago.com/another-path'):
            with self.subTest(destination=destination):
                self.assertIsNone(handler.redirect_request(request, None, 302, 'Found', {}, destination))


if __name__ == '__main__':
    unittest.main()
