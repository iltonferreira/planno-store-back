#!/usr/bin/env python3
"""Opt-in TEST HTTP checks; optional tokenization uses only the fixed official test card.

Never creates a payment, accepts production tokens, or accepts external base URLs.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import urllib.error
import urllib.parse
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Authorization must never be forwarded to another destination.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--credentials-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--tokenize-test-card', action='store_true',
                        help='Tokenize the official fictitious Visa test card once; never creates a payment.')
    args = parser.parse_args()
    if args.output.is_symlink() or args.output.exists():
        raise SystemExit('Output must be a new non-symlink path; nothing was sent.')
    source = args.credentials_file
    if source.is_symlink() or not source.is_file() or stat.S_IMODE(source.stat().st_mode) & 0o077:
        raise SystemExit('Use a private regular credentials file (mode 600).')
    credentials = {}
    for line in source.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            credentials[key.strip()] = value.strip().strip('\"\x27')
    for key in ('MP_TEST_ACCESS_TOKEN', 'MP_TEST_PUBLIC_KEY'):
        if not re.fullmatch(r'TEST-[A-Za-z0-9-]+', credentials.get(key, '')):
            raise SystemExit('Only TEST credentials are permitted; nothing was sent.')
    token = credentials['MP_TEST_ACCESS_TOKEN']
    reference = 'planno-contract-test-' + uuid.uuid4().hex
    paths = [('payment_methods', '/v1/payment_methods'),
             ('search_synthetic_reference', '/v1/payments/search?' + urllib.parse.urlencode({'external_reference': reference}))]
    if args.tokenize_test_card:
        paths.append(('tokenize_official_test_card', '/v1/card_tokens'))
    opener = urllib.request.build_opener(NoRedirect())
    observations = []
    for name, path in paths:
        method = 'POST' if name == 'tokenize_official_test_card' else 'GET'
        # Fixed fictitious data published by Mercado Pago; no arbitrary card inputs.
        payload = json.dumps({'card_number': '4235647728025682', 'security_code': '123',
                              'expiration_month': 11, 'expiration_year': 2030,
                              'cardholder': {'name': 'APRO', 'identification':
                                             {'type': 'CPF', 'number': '12345678909'}}}).encode() if method == 'POST' else None
        request = urllib.request.Request('https://api.mercadopago.com' + path,
                                         data=payload, headers={'Authorization': 'Bearer ' + token,
                                         'Accept': 'application/json', 'Content-Type': 'application/json'}, method=method)
        try:
            response = opener.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        except (urllib.error.URLError, TimeoutError, OSError):
            observations.append({'operation': name, 'result': 'connection_failed', 'authenticated_contract_validated': False})
            continue
        with response:
            body = response.read(2_000_001)
            observation = {'operation': name, 'method': method, 'http_status': response.code,
                           'body_sha256': hashlib.sha256(body).hexdigest()}
            try:
                data = json.loads(body) if len(body) <= 2_000_000 else None
            except (ValueError, UnicodeError):
                data = None
            observation['json_response'] = data is not None
            if name == 'payment_methods' and isinstance(data, list):
                observation['payment_method_count'] = len(data)
                observation['visa_present'] = any(item.get('id') == 'visa' for item in data if isinstance(item, dict))
            elif name == 'search_synthetic_reference' and isinstance(data, dict) and isinstance(data.get('results'), list):
                observation['result_count'] = len(data['results'])
                observation['synthetic_reference_only'] = True
            elif name == 'tokenize_official_test_card' and response.code in (200, 201) and isinstance(data, dict):
                observation['token_id_present'] = isinstance(data.get('id'), str) and bool(data['id'])
                observation['live_mode_false'] = data.get('live_mode') is False
                observation['luhn_validation_true'] = data.get('luhn_validation') is True
                observation['status_active'] = data.get('status') == 'active'
                observation['token_saved_or_used'] = False
                # Stop on any unexpected mode. Never use or log a token returned by this check.
                if data.get('live_mode') is not False:
                    observation['unexpected_mode_stop'] = True
            elif isinstance(data, dict):
                # Providers can reflect request secrets in arbitrary error fields.
                # Record shape/count only, never server-provided strings.
                observation['provider_error_present'] = 'error' in data
                causes = data.get('cause')
                observation['cause_count'] = len(causes) if isinstance(causes, list) else 0
            observations.append(observation)
    result = {'timestamp_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'classification': 'REAL_HTTP_TEST_CREDENTIALS_TOKENIZATION_ONLY' if args.tokenize_test_card else 'REAL_HTTP_TEST_CREDENTIALS_READ_ONLY',
              'host': 'api.mercadopago.com', 'credentials_prefix_checked': 'TEST',
              'mutating_requests': int(args.tokenize_test_card), 'payment_requests': 0, 'raw_responses_saved': False,
              'observations': observations}
    args.output.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    fd = os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as output:
        json.dump(result, output, indent=2)
        output.write('\n')
    # Explicit allowlisted summaries only, never credentials or raw response bodies.
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
