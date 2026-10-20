"""Local OAuth harness guards; no external credentials or networking."""
import importlib.util
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import urllib.parse

spec = importlib.util.spec_from_file_location('mp_oauth_guard', Path(__file__).with_name('mercadopago-oauth-test.py'))
oauth = importlib.util.module_from_spec(spec)
spec.loader.exec_module(oauth)


class FakeResponse:
    code = 200
    def __init__(self, data):
        self.body = json.dumps(data).encode()
    def __enter__(self):
        return self
    def __exit__(self, *args):
        pass
    def read(self, limit):
        return self.body[:limit]


class OAuthSandboxGuards(unittest.TestCase):
    def setUp(self):
        self.session = oauth.make_session('123', '456', now=100)
        self.data = {'access_token': 'TEST-fictitious-access', 'public_key': 'TEST-fictitious-public',
                     'refresh_token': 'fictitious-refresh', 'live_mode': False, 'user_id': 456,
                     'expires_in': 3600, 'scope': 'read write offline_access', 'token_type': 'bearer'}

    def callback(self, **values):
        return oauth.CALLBACK_PATH + '?' + urllib.parse.urlencode(values)

    def test_legitimate_callback_requires_matching_state(self):
        path = self.callback(code='fictional-code', state=self.session['state'])
        self.assertEqual({'code': 'fictional-code'}, oauth.parse_callback(path, '127.0.0.1:8765', self.session))

    def test_wrong_state_and_non_ascii_input_fail_closed(self):
        for state in ('wrong', 'é' * 50):
            self.assertIsNone(oauth.parse_callback(self.callback(code='fictional-code', state=state),
                                                  '127.0.0.1:8765', self.session))

    def test_wrong_host_and_duplicate_code_are_rejected(self):
        path = self.callback(code='fictional-code', state=self.session['state'])
        self.assertIsNone(oauth.parse_callback(path, 'attacker.example', self.session))
        self.assertIsNone(oauth.parse_callback(path + '&code=another', '127.0.0.1:8765', self.session))

    def test_expired_or_consumed_session_is_rejected(self):
        with self.assertRaises(oauth.SafeError):
            oauth.validate_session(self.session, '123', now=1000)
        self.session['consumed'] = True
        with self.assertRaises(oauth.SafeError):
            oauth.validate_session(self.session, '123', now=101)

    def test_authorization_uses_pkce_and_fixed_loopback(self):
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(oauth.authorization_url(self.session)).query)
        self.assertEqual([oauth.REDIRECT_URI], query['redirect_uri'])
        self.assertEqual(['S256'], query['code_challenge_method'])
        self.assertEqual(['https://127.0.0.1:8765/mercadopago/oauth-test/callback'], query['redirect_uri'])
        self.assertNotIn(self.session['code_verifier'], oauth.authorization_url(self.session))

    def test_valid_bearer_test_response_is_accepted(self):
        self.assertIs(self.data, oauth.validate_token_response(self.data, '456'))

    def test_production_wrong_seller_and_missing_scopes_are_rejected(self):
        for changes in ({'access_token': 'APP_USR-fictitious'}, {'live_mode': True},
                        {'user_id': 789}, {'scope': 'read'}, {'expires_in': 0}, {'refresh_token': ''}):
            with self.subTest(changes=tuple(changes)):
                with self.assertRaises(oauth.SafeError):
                    oauth.validate_token_response(dict(self.data, **changes), '456')

    def test_exchange_always_uses_test_mode_without_leaking_response(self):
        outer = self
        class Opener:
            def open(self, request, timeout):
                payload = json.loads(request.data)
                outer.assertEqual('true', payload['test_token'])
                outer.assertIs(type(payload['test_token']), str)
                outer.assertEqual(oauth.TOKEN_URL, request.full_url)
                outer.assertEqual('authorization_code', payload['grant_type'])
                outer.assertEqual(outer.session['code_verifier'], payload['code_verifier'])
                return FakeResponse(outer.data)
        data, observation = oauth.exchange_code(self.session, 'fictitious-secret', 'fictional-code', Opener())
        self.assertIsNotNone(data)
        output = json.dumps(observation)
        for secret in ('fictitious-secret', 'fictional-code', self.data['access_token'], self.data['refresh_token']):
            self.assertNotIn(secret, output)
        self.assertTrue(observation['live_mode_false'])
        self.assertFalse(observation['token_saved'])


class OAuthLifecycleGuards(unittest.TestCase):
    """Exercise the callback handler in memory; no server sockets or provider network."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='planno-oauth-unit-', dir='/private/tmp')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.credentials = self.root / 'synthetic.env'
        self.session_path = self.root / 'session.json'
        self.output = self.root / 'token.env'
        self.evidence = self.root / 'evidence.json'
        self.credentials.write_text('MP_OAUTH_CLIENT_ID=123\nMP_OAUTH_CLIENT_SECRET=synthetic-client-secret\n')
        os.chmod(self.credentials, 0o600)
        self.session = oauth.make_session('123', '456')
        self.session_path.write_text(json.dumps(self.session))
        os.chmod(self.session_path, 0o600)
        self.data = {'access_token': 'TEST-fictitious-access', 'public_key': 'TEST-fictitious-public',
                     'refresh_token': 'fictitious-refresh', 'live_mode': False, 'user_id': 456,
                     'expires_in': 3600, 'scope': 'read write offline_access', 'token_type': 'bearer'}
        self.callback_path = oauth.CALLBACK_PATH + '?' + urllib.parse.urlencode(
            {'code': 'fictional-code', 'state': self.session['state']})
        self.printed = io.StringIO()
        tls = mock.patch.object(oauth, 'build_tls_context', return_value=mock.Mock())
        tls.start()
        self.addCleanup(tls.stop)

    def fake_server(self):
        outer = self

        class FakeServer:
            def __init__(self, address, handler):
                outer.assertEqual(('127.0.0.1', 8765), address)
                self.handler = handler

            def __enter__(self):
                return self

            def __exit__(self, *_):
                pass

            def handle_request(self):
                handler = object.__new__(self.handler)
                handler.path = outer.callback_path
                handler.headers = {'Host': '127.0.0.1:8765'}
                handler.reply = lambda *_: None
                try:
                    handler.do_GET()
                except Exception:
                    self.handle_error(None, None)

        return FakeServer

    def assert_no_printed_secrets(self):
        for secret in (self.data['access_token'], self.data['refresh_token'],
                       'fictional-code', 'synthetic-client-secret'):
            self.assertNotIn(secret, self.printed.getvalue())

    def observation(self):
        return {'result': 'validated_test_response', 'token_saved': False, 'payment_requests': 0}

    def test_callback_consumes_session_before_exchange_and_commits_private_files(self):
        def exchange(*_):
            self.assertTrue(json.loads(self.session_path.read_text())['consumed'])
            self.assertTrue(self.output.exists())
            self.assertTrue(self.evidence.exists())
            return self.data, self.observation()

        with mock.patch.object(oauth, 'HTTPServer', self.fake_server()), \
                mock.patch.object(oauth, 'exchange_code', side_effect=exchange) as call, \
                contextlib.redirect_stdout(self.printed):
            oauth.serve(self.credentials, self.session_path, self.output, self.evidence)
        self.assertEqual(1, call.call_count)
        for path in (self.output, self.evidence):
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
        self.assertIn('MP_TEST_ACCESS_TOKEN=' + self.data['access_token'], self.output.read_text())
        self.assertTrue(json.loads(self.evidence.read_text())['token_saved'])
        self.assert_no_printed_secrets()

    def test_uncertain_exchange_consumes_session_blocks_retry_and_keeps_only_safe_evidence(self):
        response = (None, {'result': 'connection_failed_result_uncertain', 'token_saved': False})
        with mock.patch.object(oauth, 'HTTPServer', self.fake_server()), \
                mock.patch.object(oauth, 'exchange_code', return_value=response) as call, \
                contextlib.redirect_stdout(self.printed):
            with self.assertRaises(oauth.SafeError):
                oauth.serve(self.credentials, self.session_path, self.output, self.evidence)
            with self.assertRaises(oauth.SafeError):
                oauth.serve(self.credentials, self.session_path, self.output)
        self.assertEqual(1, call.call_count)
        self.assertTrue(json.loads(self.session_path.read_text())['consumed'])
        self.assertFalse(self.output.exists())
        self.assertFalse(json.loads(self.evidence.read_text())['token_saved'])
        self.assert_no_printed_secrets()

    def test_full_handler_path_rejects_uncertain_modes_before_saving_any_token(self):
        missing = dict(self.data)
        del missing['live_mode']
        invalid = [missing] + [dict(self.data, live_mode=value)
                               for value in (None, True, 'false', 0, 0.0, [], {})]
        invalid.append(dict(self.data, access_token='APP_USR-fictitious'))
        invalid.append(dict(self.data, public_key='APP_USR-fictitious'))
        for index, data in enumerate(invalid):
            with self.subTest(case=index):
                case = self.root / str(index)
                case.mkdir(mode=0o700)
                session_path = case / 'session.json'
                session_path.write_text(json.dumps(self.session))
                session_path.chmod(0o600)
                output = case / 'token.env'
                evidence = case / 'evidence.json'
                opener = mock.Mock()
                opener.open.return_value = FakeResponse(data)
                with mock.patch.object(oauth, 'HTTPServer', self.fake_server()), \
                        mock.patch.object(oauth.urllib.request, 'build_opener', return_value=opener), \
                        contextlib.redirect_stdout(self.printed):
                    with self.assertRaises(oauth.SafeError):
                        oauth.serve(self.credentials, session_path, output, evidence)
                opener.open.assert_called_once()
                self.assertTrue(json.loads(session_path.read_text())['consumed'])
                self.assertFalse(output.exists())
                self.assertFalse(json.loads(evidence.read_text())['token_saved'])
                self.assertEqual('response_contract_rejected', json.loads(evidence.read_text())['result'])
        self.assert_no_printed_secrets()

    def test_full_handler_path_saves_only_validated_test_false_fixture_privately(self):
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(self.data)
        with mock.patch.object(oauth, 'HTTPServer', self.fake_server()), \
                mock.patch.object(oauth.urllib.request, 'build_opener', return_value=opener), \
                contextlib.redirect_stdout(self.printed):
            oauth.serve(self.credentials, self.session_path, self.output, self.evidence)
        opener.open.assert_called_once()
        evidence = json.loads(self.evidence.read_text())
        self.assertTrue(evidence['token_saved'])
        self.assertEqual('boolean_false', evidence['live_mode_shape'])
        self.assertEqual('string', evidence['test_token_request_type'])
        self.assertTrue(json.loads(self.session_path.read_text())['consumed'])
        self.assertIn('MP_TEST_ACCESS_TOKEN=' + self.data['access_token'], self.output.read_text())
        self.assertEqual(0o600, self.output.stat().st_mode & 0o777)
        self.assert_no_printed_secrets()

    def assert_failed_fsync_discards_both_files(self, failure_number):
        calls = 0

        def fsync(_):
            nonlocal calls
            calls += 1
            if calls == failure_number:
                raise OSError('synthetic-fsync-failure')

        with mock.patch.object(oauth, 'HTTPServer', self.fake_server()), \
                mock.patch.object(oauth, 'exchange_code', return_value=(self.data, self.observation())) as call, \
                mock.patch.object(oauth.os, 'fsync', side_effect=fsync), \
                contextlib.redirect_stdout(self.printed):
            with self.assertRaises(oauth.SafeError):
                oauth.serve(self.credentials, self.session_path, self.output, self.evidence)
        self.assertEqual(1, call.call_count)
        self.assertTrue(json.loads(self.session_path.read_text())['consumed'])
        self.assertFalse(self.output.exists())
        self.assertFalse(self.evidence.exists())
        self.assertIn('"token_saved": false', self.printed.getvalue())
        self.assert_no_printed_secrets()

    def test_token_fsync_failure_discards_only_new_output_and_evidence(self):
        self.assert_failed_fsync_discards_both_files(2)

    def test_evidence_fsync_failure_cannot_report_success_or_leave_token(self):
        self.assert_failed_fsync_discards_both_files(3)

    def test_failed_cleanup_reports_unknown_and_requires_private_inspection(self):
        calls = 0

        def fsync(_):
            nonlocal calls
            calls += 1
            if calls == 2:
                raise OSError('synthetic-token-fsync-failure')

        with mock.patch.object(oauth, 'HTTPServer', self.fake_server()), \
                mock.patch.object(oauth, 'exchange_code', return_value=(self.data, self.observation())), \
                mock.patch.object(oauth.os, 'fsync', side_effect=fsync), \
                mock.patch.object(oauth, 'discard_created_file', side_effect=OSError('synthetic-unlink-failure')), \
                contextlib.redirect_stdout(self.printed):
            with self.assertRaisesRegex(oauth.SafeError, 'inspect private output'):
                oauth.serve(self.credentials, self.session_path, self.output, self.evidence)
        self.assertTrue(self.output.exists())
        self.assertTrue(json.loads(self.session_path.read_text())['consumed'])
        self.assertIn('"token_saved": "unknown"', self.printed.getvalue())
        self.assertIn('"private_output_requires_inspection": true', self.printed.getvalue())
        self.assertNotIn('"token_saved": false', self.printed.getvalue())
        self.assert_no_printed_secrets()

    def test_cleanup_preserves_replacement_inode_symlink_and_unrelated_files(self):
        self.output.write_text('handler-created-file')
        original = self.output.stat()
        identity = (original.st_dev, original.st_ino)
        original_path = self.root / 'original-inode.env'
        self.output.rename(original_path)
        self.output.write_text('unrelated-replacement')
        with self.assertRaises(oauth.SafeError):
            oauth.discard_created_file(self.output, identity)
        self.assertEqual('unrelated-replacement', self.output.read_text())
        self.assertEqual('handler-created-file', original_path.read_text())
        self.output.unlink()
        self.output.symlink_to(original_path)
        with self.assertRaises(oauth.SafeError):
            oauth.discard_created_file(self.output, identity)
        self.assertTrue(self.output.is_symlink())
        self.assertEqual('handler-created-file', original_path.read_text())
        self.output.unlink()
        original_path.rename(self.output)
        oauth.discard_created_file(self.output, identity)
        self.assertFalse(self.output.exists())
        self.assertTrue(self.credentials.exists())
        self.assertTrue(self.session_path.exists())


class OAuthTlsGuards(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='planno-oauth-tls-unit-', dir='/private/tmp')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.certificate = self.root / 'synthetic-cert.pem'
        self.key = self.root / 'synthetic-key.pem'
        for path in (self.certificate, self.key):
            path.write_text('synthetic-content-not-a-real-certificate-or-key')
            os.chmod(path, 0o600)

    def test_missing_tls_material_stops_before_credentials_or_listener(self):
        with mock.patch.object(oauth, 'load_credentials') as credentials, \
                mock.patch.object(oauth, 'HTTPServer') as listener:
            with self.assertRaises(oauth.SafeError):
                oauth.serve(self.root / 'absent.env', self.root / 'absent-session.json', self.root / 'new-output.env')
        credentials.assert_not_called()
        listener.assert_not_called()

    def test_either_missing_certificate_or_key_fails_closed(self):
        for certificate, key in ((None, self.key), (self.certificate, None)):
            with self.subTest(certificate_present=certificate is not None):
                with self.assertRaises(oauth.SafeError):
                    oauth.build_tls_context(certificate, key)

    def test_permissive_certificate_or_key_is_rejected_before_loading_tls(self):
        for path in (self.certificate, self.key):
            with self.subTest(file=path.name), mock.patch.object(oauth.ssl, 'SSLContext') as context:
                os.chmod(path, 0o644)
                try:
                    with self.assertRaises(oauth.SafeError):
                        oauth.build_tls_context(self.certificate, self.key)
                    context.assert_not_called()
                finally:
                    os.chmod(path, 0o600)

    def test_symlink_certificate_or_key_is_rejected_without_loading_tls(self):
        link = self.root / 'synthetic-link.pem'
        link.symlink_to(self.key)
        with mock.patch.object(oauth.ssl, 'SSLContext') as context:
            with self.assertRaises(oauth.SafeError):
                oauth.build_tls_context(self.certificate, link)
            context.assert_not_called()

    def test_tls_minimum_and_validated_open_descriptors_are_used(self):
        context = mock.Mock()

        def load(certfile, keyfile, password):
            self.assertEqual(b'', password())
            for path in (certfile, keyfile):
                self.assertTrue(path.startswith('/dev/fd/'))
                descriptor = int(path.removeprefix('/dev/fd/'))
                self.assertEqual(0o600, os.fstat(descriptor).st_mode & 0o777)

        context.load_cert_chain.side_effect = load
        with mock.patch.object(oauth.ssl, 'SSLContext', return_value=context) as constructor:
            self.assertIs(context, oauth.build_tls_context(self.certificate, self.key))
        constructor.assert_called_once_with(oauth.ssl.PROTOCOL_TLS_SERVER)
        self.assertEqual(oauth.ssl.TLSVersion.TLSv1_2, context.minimum_version)
        context.load_cert_chain.assert_called_once()

    def test_old_plain_http_session_is_rejected(self):
        session = oauth.make_session('123', '456', now=100)
        session['redirect_uri'] = 'http://127.0.0.1:8765/mercadopago/oauth-test/callback'
        with self.assertRaises(oauth.SafeError):
            oauth.validate_session(session, '123', now=101)


class OAuthRedirectGuards(unittest.TestCase):
    """Validate preparatory URI shapes; these tests never open or authorize a tunnel."""
    TUNNEL_URI = 'https://planno-fictitious-test.trycloudflare.com/mercadopago/oauth-test/callback'

    def test_only_fixed_loopback_or_single_label_https_tunnel_shape_is_accepted(self):
        for uri in (oauth.REDIRECT_URI, self.TUNNEL_URI,
                    'https://a.trycloudflare.com' + oauth.CALLBACK_PATH,
                    'https://' + 'a' * 63 + '.trycloudflare.com' + oauth.CALLBACK_PATH):
            with self.subTest(uri=uri):
                self.assertEqual(uri, oauth.validate_redirect_uri(uri))

    def test_other_domains_and_crafted_authorities_or_paths_are_rejected(self):
        invalid = (None, '', 'https://production.example.com' + oauth.CALLBACK_PATH,
                   'https://api.mercadopago.com' + oauth.CALLBACK_PATH,
                   'https://localhost:8765' + oauth.CALLBACK_PATH,
                   self.TUNNEL_URI.replace('https:', 'http:'),
                   self.TUNNEL_URI.replace('https:', 'javascript:'),
                   self.TUNNEL_URI.replace('https://', 'https://user:password@'),
                   self.TUNNEL_URI.replace('.com/', '.com:443/'),
                   self.TUNNEL_URI + '?code=injected', self.TUNNEL_URI + '#fragment',
                   self.TUNNEL_URI + '/', self.TUNNEL_URI.replace('/oauth-test/', '/other/'),
                   self.TUNNEL_URI.replace('.trycloudflare.', '.nested.trycloudflare.'),
                   self.TUNNEL_URI.replace('.trycloudflare.com', '.trycloudflare.com.evil.example'),
                   self.TUNNEL_URI.replace('.trycloudflare.com', '.trycloudflare.com.'),
                   self.TUNNEL_URI.replace('https://', '//'),
                   self.TUNNEL_URI.replace('/mercadopago/', '/%6dercadopago/'))
        for uri in invalid:
            with self.subTest(uri=uri):
                with self.assertRaises(oauth.SafeError):
                    oauth.validate_redirect_uri(uri)

    def test_nested_uppercase_edge_hyphen_and_overlong_labels_are_rejected(self):
        for label in ('two.labels', '-leading', 'trailing-', 'Uppercase', 'with_underscore', 'a' * 64):
            with self.subTest(label=label):
                with self.assertRaises(oauth.SafeError):
                    oauth.validate_redirect_uri('https://' + label + '.trycloudflare.com' + oauth.CALLBACK_PATH)

    def test_tampered_session_redirect_is_rejected_before_exchange(self):
        session = oauth.make_session('123', '456', now=100, redirect_uri=self.TUNNEL_URI)
        self.assertIs(session, oauth.validate_session(session, '123', now=101))
        for uri in (self.TUNNEL_URI + '?injected=true',
                    'https://production.example.com' + oauth.CALLBACK_PATH,
                    'http://127.0.0.1:8765' + oauth.CALLBACK_PATH):
            with self.subTest(uri=uri):
                with self.assertRaises(oauth.SafeError):
                    oauth.validate_session(dict(session, redirect_uri=uri), '123', now=101)

    def test_authorization_and_mock_exchange_use_the_same_validated_session_uri(self):
        session = oauth.make_session('123', '456', now=100, redirect_uri=self.TUNNEL_URI)
        url = oauth.authorization_url(session)
        self.assertEqual([self.TUNNEL_URI], urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)['redirect_uri'])
        outer = self
        data = {'access_token': 'TEST-fictitious-access', 'public_key': 'TEST-fictitious-public',
                'refresh_token': 'fictitious-refresh', 'live_mode': False, 'user_id': 456,
                'expires_in': 3600, 'scope': 'read write offline_access', 'token_type': 'bearer'}

        class Opener:
            def open(self, request, timeout):
                outer.assertEqual(oauth.TOKEN_URL, request.full_url)
                outer.assertEqual(outer.TUNNEL_URI, json.loads(request.data)['redirect_uri'])
                outer.assertEqual('true', json.loads(request.data)['test_token'])
                outer.assertIs(type(json.loads(request.data)['test_token']), str)
                return FakeResponse(data)

        accepted, observation = oauth.exchange_code(session, 'fictitious-secret', 'fictional-code', Opener())
        self.assertIsNotNone(accepted)
        self.assertEqual('validated_test_response', observation['result'])

    def test_prepare_persists_validated_uri_privately_without_network(self):
        with tempfile.TemporaryDirectory(prefix='planno-oauth-redirect-unit-', dir='/private/tmp') as directory:
            root = Path(directory)
            credentials = root / 'synthetic.env'
            session_path = root / 'session.json'
            credentials.write_text('MP_OAUTH_CLIENT_ID=123\nMP_OAUTH_CLIENT_SECRET=synthetic-client-secret\n')
            os.chmod(credentials, 0o600)
            with mock.patch.object(oauth.urllib.request, 'build_opener') as network:
                url = oauth.prepare(credentials, '456', session_path, redirect_uri=self.TUNNEL_URI)
            network.assert_not_called()
            self.assertEqual(0o600, session_path.stat().st_mode & 0o777)
            self.assertEqual(self.TUNNEL_URI, json.loads(session_path.read_text())['redirect_uri'])
            self.assertEqual([self.TUNNEL_URI], urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)['redirect_uri'])


class OAuthSafeDiagnosticsGuards(unittest.TestCase):
    """Response diagnostics use fixed local reasons, never provider-supplied values."""
    PRIVATE = 'hostile-fictitious-private-value'

    def setUp(self):
        self.session = oauth.make_session('123', '456', now=100)
        self.data = {'access_token': 'TEST-fictitious-access', 'public_key': 'TEST-fictitious-public',
                     'refresh_token': 'fictitious-refresh', 'live_mode': False, 'user_id': 456,
                     'expires_in': 3600, 'scope': 'read write offline_access', 'token_type': 'bearer'}

    def assert_safe_rejection(self, data, expected_reason, raw=None):
        response = FakeResponse(data)
        if raw is not None:
            response.body = raw

        class Opener:
            def open(self, request, timeout):
                return response

        accepted, observation = oauth.exchange_code(self.session, 'fictitious-client-secret', 'fictional-code', Opener())
        self.assertIsNone(accepted)
        self.assertEqual('response_contract_rejected', observation['result'])
        self.assertEqual(expected_reason, observation['rejection_reason'])
        self.assertFalse(observation['token_saved'])
        self.assertFalse(observation['raw_response_saved'])
        diagnostic = json.dumps(observation)
        for private in (self.PRIVATE, 'fictitious-client-secret', 'fictional-code',
                        self.data['access_token'], self.data['public_key'], self.data['refresh_token']):
            self.assertNotIn(private, diagnostic)

    def test_invalid_json_has_fixed_reason_without_reflecting_raw_provider_text(self):
        self.assert_safe_rejection(None, 'json_invalid', raw=('invalid-json:' + self.PRIVATE).encode())

    def test_non_object_json_has_fixed_reason_without_reflecting_values(self):
        self.assert_safe_rejection([self.PRIVATE], 'json_not_object')

    def test_non_test_access_token_has_fixed_reason_without_reflection(self):
        self.assert_safe_rejection(dict(self.data, access_token='APP_USR-' + self.PRIVATE), 'access_token_not_test')

    def test_non_test_public_key_has_fixed_reason_without_reflection(self):
        self.assert_safe_rejection(dict(self.data, public_key='APP_USR-' + self.PRIVATE), 'public_key_not_test')

    def test_live_mode_failure_has_fixed_reason_without_reflection(self):
        self.assert_safe_rejection(dict(self.data, live_mode=self.PRIVATE), 'live_mode_not_false')

    def test_seller_mismatch_has_fixed_reason_without_reflecting_identifier(self):
        self.assert_safe_rejection(dict(self.data, user_id='789-' + self.PRIVATE), 'authorized_seller_mismatch')

    def test_invalid_expiration_has_fixed_reason_without_reflection(self):
        self.assert_safe_rejection(dict(self.data, expires_in=self.PRIVATE), 'expiration_invalid')

    def test_invalid_refresh_token_has_fixed_reason_without_reflection(self):
        self.assert_safe_rejection(dict(self.data, refresh_token={'private': self.PRIVATE}), 'refresh_token_invalid')

    def test_missing_required_scopes_has_fixed_reason_without_reflecting_scopes(self):
        self.assert_safe_rejection(dict(self.data, scope='read ' + self.PRIVATE), 'required_scopes_missing')

    def test_unsupported_token_type_has_fixed_reason_without_reflection(self):
        self.assert_safe_rejection(dict(self.data, token_type='Bearer-' + self.PRIVATE), 'token_type_unsupported')

    def test_multiple_invalid_predicates_report_only_first_fixed_reason(self):
        data = dict(self.data, access_token='APP_USR-' + self.PRIVATE,
                    public_key='APP_USR-' + self.PRIVATE, live_mode=True,
                    user_id=789, expires_in=0, scope=self.PRIVATE)
        self.assert_safe_rejection(data, 'access_token_not_test')

    def test_direct_validation_exception_remains_safe_error_without_provider_values(self):
        with self.assertRaises(oauth.SafeError) as caught:
            oauth.validate_token_response(dict(self.data, access_token='APP_USR-' + self.PRIVATE), '456')
        self.assertNotIn(self.PRIVATE, str(caught.exception))


class OAuthEnvironmentContractGuards(unittest.TestCase):
    """Published request type and fail-closed response mode, with fake HTTP only."""

    def setUp(self):
        self.session = oauth.make_session('123', '456', now=100)
        self.data = {'access_token': 'TEST-fictitious-access', 'public_key': 'TEST-fictitious-public',
                     'refresh_token': 'fictitious-refresh', 'live_mode': False, 'user_id': 456,
                     'expires_in': 3600, 'scope': 'read write offline_access', 'token_type': 'bearer'}
        self.private_marker = 'hostile-fictitious-private-value'

    def exchange(self, data=None, status=200, raw=None, failure=None):
        response = FakeResponse(self.data if data is None else data)
        response.code = status
        if raw is not None:
            response.body = raw
        opener = mock.Mock()
        if failure is not None:
            opener.open.side_effect = failure
        else:
            opener.open.return_value = response
        accepted, observation = oauth.exchange_code(
            self.session, 'fictitious-client-secret', 'fictional-code', opener)
        opener.open.assert_called_once()
        encoded = json.dumps(observation)
        for value in (self.private_marker, 'fictitious-client-secret', 'fictional-code',
                      self.data['access_token'], self.data['public_key'], self.data['refresh_token']):
            self.assertNotIn(value, encoded)
        self.assertFalse(observation['token_saved'])
        self.assertFalse(observation['raw_response_saved'])
        return accepted, observation, opener.open.call_args.args[0]

    def assert_mode_rejected(self, data, shape):
        accepted, observation, _ = self.exchange(data)
        self.assertIsNone(accepted)
        self.assertEqual('live_mode_not_false', observation['rejection_reason'])
        self.assertEqual(shape, observation['live_mode_shape'])

    def test_test_token_is_documented_json_string(self):
        accepted, observation, request = self.exchange()
        self.assertIsNotNone(accepted)
        payload = json.loads(request.data)
        self.assertEqual('true', payload['test_token'])
        self.assertIs(type(payload['test_token']), str)
        self.assertEqual('string', observation['test_token_request_type'])
        self.assertEqual('authorization_code', payload['grant_type'])
        self.assertEqual(self.session['redirect_uri'], payload['redirect_uri'])
        self.assertEqual(self.session['code_verifier'], payload['code_verifier'])

    def test_live_mode_false_and_test_credentials_are_accepted(self):
        accepted, observation, _ = self.exchange()
        self.assertIsNotNone(accepted)
        self.assertEqual('boolean_false', observation['live_mode_shape'])
        self.assertTrue(observation['live_mode_false'])

    def test_live_mode_true_is_rejected_even_with_test_prefixes(self):
        self.assert_mode_rejected(dict(self.data, live_mode=True), 'boolean_true')

    def test_live_mode_absent_is_rejected(self):
        data = dict(self.data)
        del data['live_mode']
        self.assert_mode_rejected(data, 'absent')

    def test_live_mode_null_is_rejected(self):
        self.assert_mode_rejected(dict(self.data, live_mode=None), 'null')

    def test_string_false_and_other_strings_are_rejected_without_reflection(self):
        for value in ('false', 'true', self.private_marker, ''):
            with self.subTest(kind='string'):
                self.assert_mode_rejected(dict(self.data, live_mode=value), 'string')

    def test_numeric_zero_is_not_boolean_false(self):
        for value, shape in ((0, 'integer'), (1, 'integer'), (0.0, 'number'), (1.0, 'number')):
            with self.subTest(shape=shape):
                self.assert_mode_rejected(dict(self.data, live_mode=value), shape)

    def test_composite_live_mode_values_are_rejected_without_reflection(self):
        for value, shape in (([self.private_marker], 'array'), ({'private': self.private_marker}, 'object')):
            with self.subTest(shape=shape):
                self.assert_mode_rejected(dict(self.data, live_mode=value), shape)

    def test_production_access_or_public_key_rejected_even_when_mode_false(self):
        for field, reason in (('access_token', 'access_token_not_test'), ('public_key', 'public_key_not_test')):
            with self.subTest(field=field):
                accepted, observation, _ = self.exchange(dict(self.data, **{field: 'APP_USR-' + self.private_marker}))
                self.assertIsNone(accepted)
                self.assertEqual(reason, observation['rejection_reason'])

    def test_invalid_json_or_utf8_response_rejected_without_reflection(self):
        for raw in (self.private_marker.encode(), b'\xff'):
            with self.subTest(kind='invalid_json'):
                accepted, observation, _ = self.exchange(raw=raw)
                self.assertIsNone(accepted)
                self.assertEqual('json_invalid', observation['rejection_reason'])
                self.assertNotIn('live_mode_shape', observation)

    def test_non_object_json_has_safe_shape(self):
        accepted, observation, _ = self.exchange([self.private_marker])
        self.assertIsNone(accepted)
        self.assertEqual('json_not_object', observation['rejection_reason'])
        self.assertEqual('response_not_object', observation['live_mode_shape'])

    def test_http_failures_rejected_without_parsing_body_or_retry(self):
        for status in (400, 401, 403, 429, 500):
            with self.subTest(status=status):
                accepted, observation, _ = self.exchange(status=status, raw=self.private_marker.encode())
                self.assertIsNone(accepted)
                self.assertEqual(status, observation['http_status'])
                self.assertEqual('http_or_size_rejected', observation['result'])
                self.assertNotIn('live_mode_shape', observation)

    def test_urllib_http_error_is_sanitized(self):
        error = oauth.urllib.error.HTTPError(oauth.TOKEN_URL, 401, self.private_marker, {},
                                           io.BytesIO(self.private_marker.encode()))
        accepted, observation, _ = self.exchange(failure=error)
        self.assertIsNone(accepted)
        self.assertEqual(401, observation['http_status'])
        self.assertEqual('http_or_size_rejected', observation['result'])

    def test_network_errors_and_timeouts_remain_uncertain_without_retry(self):
        for error in (oauth.urllib.error.URLError(self.private_marker), TimeoutError(self.private_marker),
                      OSError(self.private_marker)):
            with self.subTest(kind=type(error).__name__):
                accepted, observation, _ = self.exchange(failure=error)
                self.assertIsNone(accepted)
                self.assertEqual('connection_failed_result_uncertain', observation['result'])
                self.assertNotIn('live_mode_shape', observation)


if __name__ == '__main__':
    unittest.main()
