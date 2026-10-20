#!/usr/bin/env python3
"""Opt-in OAuth for an explicitly authorized Mercado Pago test seller only.

prepare --credentials-file /private/app.env --expected-seller-id 123
        --session /private/new-session.json
serve --credentials-file /private/app.env --session /private/new-session.json
      --output /private/new-test-token.env --tls-cert-file /private/local-cert.pem
      --tls-key-file /private/local-key.pem [--evidence /private/new-evidence.json]

App credentials must already exist; this script never activates, renews, or
changes them. Files and their parent directories must be private (600/700).
The default loopback redirect must be accepted by Mercado Pago separately; the
script never registers it or publishes an endpoint. Open the printed URL and
approve access only after checking the authorized test seller in the browser.
The callback is HTTPS only on 127.0.0.1:8765, with TLS 1.2 or newer. Supply an
existing local certificate/key; this script never installs global trust or
disables certificate verification. A self-signed certificate may require a
browser decision by the user. Existing HTTP sessions must be prepared again.
Preparation may accept --redirect-uri https://<one-label>.trycloudflare.com
followed by the exact callback path. Using that redirect requires the human's
explicit approval for temporary public Cloudflare tunnel exposure FIRST; URL
validation is not authorization. This script never creates a tunnel, changes
external configuration, or binds a public interface. Any separately approved
tunnel must forward only to the local HTTPS callback and preserve the expected
127.0.0.1:8765 origin Host header. Other public hosts/paths are rejected.
There is one code exchange, no retry, refresh, payment, or production token use.
Unexpected responses are rejected without saving tokens or provider messages.
If the exchange result is uncertain, the session remains consumed; start a new
authorization rather than retrying the code. Python's ordinary memory is not a
secure enclave. Keep the resulting private files outside Git and backups.
"""

import argparse
import base64
import datetime
import fcntl
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import ssl
import stat
import time
import urllib.error
import urllib.parse
import urllib.request


AUTHORIZATION_URL = 'https://auth.mercadopago.com/authorization'
TOKEN_URL = 'https://api.mercadopago.com/oauth/token'
CALLBACK_HOST = '127.0.0.1'
CALLBACK_PORT = 8765
CALLBACK_PATH = '/mercadopago/oauth-test/callback'
REDIRECT_URI = f'https://{CALLBACK_HOST}:{CALLBACK_PORT}{CALLBACK_PATH}'
SESSION_SECONDS = 15 * 60
SERVER_SECONDS = 5 * 60
MAX_RESPONSE_BYTES = 64 * 1024
REQUIRED_SCOPES = frozenset(('read', 'write', 'offline_access'))
SESSION_KEYS = frozenset(('version', 'client_id', 'expected_seller_id',
                          'redirect_uri', 'state', 'code_verifier', 'created_at',
                          'expires_at', 'consumed'))


class SafeError(Exception):
    """Only locally authored, secret-free messages may be used here."""


class ResponseContractError(SafeError):
    """A fixed local reason identifies rejection without retaining provider data."""

    def __init__(self, reason, message):
        super().__init__(message)
        self.reason = reason


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_redirect_uri(redirect_uri):
    if redirect_uri == REDIRECT_URI:
        return redirect_uri
    # An exact full-string match excludes userinfo, ports, extra labels,
    # queries, fragments, escapes, whitespace and arbitrary external URLs.
    label = r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?'
    if isinstance(redirect_uri, str) and re.fullmatch(
            rf'https://{label}\.trycloudflare\.com{re.escape(CALLBACK_PATH)}', redirect_uri):
        return redirect_uri
    raise SafeError('Redirect must be the fixed local HTTPS callback or an explicitly approved temporary Cloudflare callback.')


def private_parent(path):
    path = Path(path).absolute()
    for component in (path.parent, *path.parent.parents):
        if component.is_symlink():
            raise SafeError('Symlink directories are not permitted.')
    if not path.parent.is_dir() or stat.S_IMODE(path.parent.stat().st_mode) & 0o077:
        raise SafeError('Create a private output/input directory (mode 700) first.')
    return path


def check_new_path(path):
    path = private_parent(path)
    if path.is_symlink() or path.exists():
        raise SafeError('Output/session must be a new non-symlink path.')
    return path


def open_private(path, writable=False):
    path = private_parent(path)
    flags = (os.O_RDWR if writable else os.O_RDONLY) | os.O_NOFOLLOW | os.O_NONBLOCK
    fd = os.open(path, flags)
    info = os.fstat(fd)
    if (not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600
            or info.st_uid != os.getuid() or info.st_size > MAX_RESPONSE_BYTES):
        os.close(fd)
        raise SafeError('Input must be an owned regular file with mode 600 and bounded size.')
    return os.fdopen(fd, 'r+' if writable else 'r', encoding='utf-8')


def build_tls_context(tls_cert_file, tls_key_file):
    if tls_cert_file is None or tls_key_file is None:
        raise SafeError('A private local TLS certificate and key are required before listening.')
    # Keep the validated descriptors open while OpenSSL loads them, so a path
    # replacement cannot substitute an unvalidated file between check and use.
    try:
        with open_private(tls_cert_file) as certificate, open_private(tls_key_file) as key:
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            context.load_cert_chain(certfile=f'/dev/fd/{certificate.fileno()}',
                                    keyfile=f'/dev/fd/{key.fileno()}',
                                    password=lambda: b'')  # Never prompt for a key password.
            return context
    except (OSError, ssl.SSLError, ValueError):
        raise SafeError('Private local TLS certificate/key could not be loaded; no listener was started.') from None


def load_credentials(path):
    with open_private(path) as source:
        values = {}
        for line in source:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' not in line:
                raise SafeError('Invalid credentials file format.')
            key, value = line.split('=', 1)
            key, value = key.strip(), value.strip()
            if key in values:
                raise SafeError('Duplicate credential key.')
            if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
                value = value[1:-1]
            values[key] = value
    client_id = values.get('MP_OAUTH_CLIENT_ID', '')
    client_secret = values.get('MP_OAUTH_CLIENT_SECRET', '')
    if not re.fullmatch(r'[1-9][0-9]{0,39}', client_id):
        raise SafeError('MP_OAUTH_CLIENT_ID must be a positive numeric application ID.')
    if not re.fullmatch(r'[A-Za-z0-9_-]{8,4096}', client_secret):
        raise SafeError('MP_OAUTH_CLIENT_SECRET is missing or has an unsupported format.')
    return client_id, client_secret


def make_session(client_id, expected_seller_id, now=None, redirect_uri=REDIRECT_URI):
    if not re.fullmatch(r'[1-9][0-9]{0,39}', str(expected_seller_id)):
        raise SafeError('Expected seller must be an authorized positive numeric test ID.')
    now = int(time.time()) if now is None else now
    redirect_uri = validate_redirect_uri(redirect_uri)
    return {'version': 1, 'client_id': client_id,
            'expected_seller_id': str(expected_seller_id), 'redirect_uri': redirect_uri,
            'state': secrets.token_urlsafe(32), 'code_verifier': secrets.token_urlsafe(64),
            'created_at': now, 'expires_at': now + SESSION_SECONDS, 'consumed': False}


def validate_session(session, client_id, now=None):
    now = int(time.time()) if now is None else now
    if not isinstance(session, dict) or set(session) != SESSION_KEYS:
        raise SafeError('Invalid session schema.')
    validate_redirect_uri(session['redirect_uri'])
    if (type(session['version']) is not int or session['version'] != 1
            or session['client_id'] != client_id
            or session['consumed'] is not False):
        raise SafeError('Session does not match this application/callback or was consumed.')
    if not isinstance(session['expected_seller_id'], str) or not re.fullmatch(
            r'[1-9][0-9]{0,39}', session['expected_seller_id']):
        raise SafeError('Invalid expected test seller ID.')
    for name, minimum, maximum in (('state', 43, 128), ('code_verifier', 43, 128)):
        if not isinstance(session[name], str) or not re.fullmatch(
                rf'[A-Za-z0-9_-]{{{minimum},{maximum}}}', session[name]):
            raise SafeError('Invalid session security parameters.')
    if (type(session['created_at']) is not int or type(session['expires_at']) is not int
            or session['expires_at'] - session['created_at'] != SESSION_SECONDS
            or session['created_at'] > now or session['expires_at'] <= now):
        raise SafeError('Session expired or has invalid timestamps; prepare a new session.')
    return session


def authorization_url(session):
    redirect_uri = validate_redirect_uri(session['redirect_uri'])
    challenge = base64.urlsafe_b64encode(hashlib.sha256(
        session['code_verifier'].encode('ascii')).digest()).rstrip(b'=').decode('ascii')
    return AUTHORIZATION_URL + '?' + urllib.parse.urlencode({
        'client_id': session['client_id'], 'response_type': 'code', 'platform_id': 'mp',
        'state': session['state'], 'redirect_uri': redirect_uri,
        'code_challenge': challenge, 'code_challenge_method': 'S256'})


def create_private(path):
    path = check_new_path(path)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    return os.fdopen(fd, 'w', encoding='utf-8')


def write_session(handle, session):
    handle.seek(0)
    json.dump(session, handle, separators=(',', ':'))
    handle.write('\n')
    handle.truncate()
    handle.flush()
    os.fsync(handle.fileno())


def discard_created_file(path, identity):
    """Delete only the inode created by this handler, never another file at that path."""
    if identity is None:
        return
    try:
        current = Path(path).lstat()
    except FileNotFoundError:
        return
    if (current.st_dev, current.st_ino) != identity:
        raise SafeError('Private output changed during cleanup; inspect it before proceeding.')
    Path(path).unlink()


def prepare(credentials_file, expected_seller_id, session_path, redirect_uri=REDIRECT_URI):
    check_new_path(session_path)
    validate_redirect_uri(redirect_uri)
    client_id, _ = load_credentials(credentials_file)
    session = make_session(client_id, expected_seller_id, redirect_uri=redirect_uri)
    with create_private(session_path) as output:
        write_session(output, session)
    return authorization_url(session)


def live_mode_shape(data):
    """Classify presence/type using local constants; never retain provider values."""
    if not isinstance(data, dict):
        return 'response_not_object'
    if 'live_mode' not in data:
        return 'absent'
    value = data['live_mode']
    if value is None:
        return 'null'
    if type(value) is bool:
        return 'boolean_true' if value else 'boolean_false'
    for value_type, shape in ((str, 'string'), (int, 'integer'), (float, 'number'),
                              (list, 'array'), (dict, 'object')):
        if type(value) is value_type:
            return shape
    return 'unexpected_type'


def validate_token_response(data, expected_seller_id):
    if not isinstance(data, dict):
        raise ResponseContractError('json_not_object', 'OAuth response was not a JSON object; no token was saved.')
    for key in ('access_token', 'public_key'):
        if not isinstance(data.get(key), str) or not re.fullmatch(r'TEST-[A-Za-z0-9-]+', data[key]):
            reason = 'access_token_not_test' if key == 'access_token' else 'public_key_not_test'
            raise ResponseContractError(reason, 'OAuth response did not contain exclusively TEST credentials; no token was saved.')
    if data.get('live_mode') is not False:
        raise ResponseContractError('live_mode_not_false', 'OAuth response did not confirm live_mode=false; no token was saved.')
    user_id = data.get('user_id')
    if (type(user_id) not in (str, int) or str(user_id) != expected_seller_id):
        raise ResponseContractError('authorized_seller_mismatch', 'OAuth seller did not match the authorized test seller; no token was saved.')
    expires = data.get('expires_in')
    if type(expires) is not int or not 0 < expires <= 366 * 24 * 60 * 60:
        raise ResponseContractError('expiration_invalid', 'Invalid OAuth expiration; no token was saved.')
    refresh = data.get('refresh_token')
    if not isinstance(refresh, str) or not re.fullmatch(r'[A-Za-z0-9._~-]{1,4096}', refresh):
        raise ResponseContractError('refresh_token_invalid', 'Missing or invalid refresh token; no token was saved.')
    if (not isinstance(data.get('scope'), str)
            or not REQUIRED_SCOPES.issubset(set(data['scope'].split()))):
        raise ResponseContractError('required_scopes_missing', 'Required OAuth scopes were not granted; no token was saved.')
    if not isinstance(data.get('token_type'), str) or data['token_type'].lower() != 'bearer':
        raise ResponseContractError('token_type_unsupported', 'Unsupported OAuth token type; no token was saved.')
    return data


def exchange_code(session, client_secret, code, opener=None):
    payload = {'client_id': session['client_id'], 'client_secret': client_secret,
               'grant_type': 'authorization_code', 'code': code,
               'redirect_uri': validate_redirect_uri(session['redirect_uri']),
               'code_verifier': session['code_verifier'],
               # Published /oauth/token schema defines this parameter as string.
               'test_token': 'true'}
    request = urllib.request.Request(TOKEN_URL, data=json.dumps(payload).encode('utf-8'),
                                     headers={'Content-Type': 'application/json',
                                              'Accept': 'application/json'}, method='POST')
    opener = opener or urllib.request.build_opener(NoRedirect())
    observation = {'operation': 'oauth_test_code_exchange', 'test_token_true': True,
                   'test_token_request_type': 'string',
                   'host': 'api.mercadopago.com', 'method': 'POST', 'payment_requests': 0,
                   'raw_response_saved': False, 'token_saved': False}
    try:
        response = opener.open(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    except (urllib.error.URLError, TimeoutError, OSError):
        observation['result'] = 'connection_failed_result_uncertain'
        return None, observation
    try:
        with response:
            observation['http_status'] = response.code
            body = response.read(MAX_RESPONSE_BYTES + 1)
            observation['body_sha256'] = hashlib.sha256(body).hexdigest()
        if observation['http_status'] != 200 or len(body) > MAX_RESPONSE_BYTES:
            observation['result'] = 'http_or_size_rejected'
            return None, observation
        data = json.loads(body)
        observation['live_mode_shape'] = live_mode_shape(data)
        validate_token_response(data, session['expected_seller_id'])
    except ResponseContractError as error:
        observation['result'] = 'response_contract_rejected'
        observation['rejection_reason'] = error.reason
        return None, observation
    except (ValueError, UnicodeError):
        observation['result'] = 'response_contract_rejected'
        observation['rejection_reason'] = 'json_invalid'
        return None, observation
    except (TimeoutError, OSError):
        observation['result'] = 'response_read_failed_result_uncertain'
        return None, observation
    observation.update({'result': 'validated_test_response', 'test_prefixes_checked': True,
                        'live_mode_false': True, 'authorized_seller_matches': True,
                        'required_scopes_present': True, 'expires_in_positive': True,
                        'refresh_token_present': True})
    return data, observation


def token_env(data, now=None):
    now = int(time.time()) if now is None else now
    return '\n'.join((
        'MP_TEST_ACCESS_TOKEN=' + data['access_token'],
        'MP_TEST_PUBLIC_KEY=' + data['public_key'],
        'MP_TEST_REFRESH_TOKEN=' + data['refresh_token'],
        'MP_TEST_SELLER_ID=' + str(data['user_id']),
        'MP_TEST_TOKEN_EXPIRES_AT=' + str(now + data['expires_in']), ''))


def parse_callback(path, host, session):
    if host != f'{CALLBACK_HOST}:{CALLBACK_PORT}' or len(path) > 8192:
        return None
    parsed = urllib.parse.urlsplit(path)
    if parsed.scheme or parsed.netloc or parsed.fragment or parsed.path != CALLBACK_PATH:
        return None
    try:
        query = urllib.parse.parse_qs(parsed.query, strict_parsing=True,
                                     keep_blank_values=True, max_num_fields=10)
    except ValueError:
        return None
    if (any(len(values) != 1 for values in query.values()) or 'state' not in query
            or not re.fullmatch(r'[A-Za-z0-9_-]{43,128}', query['state'][0])
            or not hmac.compare_digest(query['state'][0], session['state'])):
        return None
    if 'error' in query and 'code' not in query:
        return {'denied': True}
    code = query.get('code', [''])[0]
    if ('error' in query or not re.fullmatch(r'[A-Za-z0-9._~-]{1,4096}', code)):
        return None
    return {'code': code}


def serve(credentials_file, session_path, output_path, evidence_path=None,
          tls_cert_file=None, tls_key_file=None):
    output_path = check_new_path(output_path)
    if evidence_path is not None:
        evidence_path = check_new_path(evidence_path)
        if evidence_path == output_path:
            raise SafeError('Token and evidence paths must differ.')
    tls_context = build_tls_context(tls_cert_file, tls_key_file)
    client_id, client_secret = load_credentials(credentials_file)
    with open_private(session_path, writable=True) as session_file:
        try:
            fcntl.flock(session_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SafeError('Another process is already using this session.') from None
        session = validate_session(json.load(session_file), client_id)
        deadline = min(time.monotonic() + SERVER_SECONDS,
                       time.monotonic() + session['expires_at'] - time.time())
        result = {'done': False, 'success': False, 'observation': None, 'cleanup_failed': False}

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                pass

            def reply(self, status, text):
                body = text.encode('utf-8')
                self.send_response(status)
                self.send_header('Content-Type', 'text/plain; charset=utf-8')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('Referrer-Policy', 'no-referrer')
                self.send_header('X-Content-Type-Options', 'nosniff')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                try:
                    self.wfile.write(body)
                except OSError:
                    pass

            def do_GET(self):
                callback = parse_callback(self.path, self.headers.get('Host'), session)
                if callback is None or result['done'] or time.monotonic() >= deadline:
                    self.reply(400, 'Callback rejected. No credentials are shown here.')
                    return
                result['done'] = True
                session['consumed'] = True
                write_session(session_file, session)
                if callback.get('denied'):
                    result['observation'] = {'result': 'authorization_denied', 'token_saved': False,
                                             'payment_requests': 0, 'http_requests': 0}
                    self.reply(403, 'Authorization was denied. No token was saved.')
                    return
                # Reserve all destination files before any authenticated request.
                output_identity = evidence_identity = None
                completed = False
                try:
                    with create_private(output_path) as output:
                        info = os.fstat(output.fileno())
                        output_identity = (info.st_dev, info.st_ino)
                        evidence = None
                        try:
                            if evidence_path:
                                evidence = create_private(evidence_path)
                                info = os.fstat(evidence.fileno())
                                evidence_identity = (info.st_dev, info.st_ino)
                            data, observation = exchange_code(session, client_secret, callback['code'])
                            if data is not None:
                                output.write(token_env(data))
                                output.flush()
                                os.fsync(output.fileno())
                                observation['token_saved'] = True
                            if evidence:
                                observation['timestamp_utc'] = datetime.datetime.now(
                                    datetime.timezone.utc).isoformat()
                                json.dump(observation, evidence, indent=2)
                                evidence.write('\n')
                                evidence.flush()
                                os.fsync(evidence.fileno())
                            result['observation'] = observation
                        finally:
                            if evidence:
                                evidence.close()
                    # Commit only after both files have been flushed and closed successfully.
                    result['success'] = data is not None
                    completed = True
                finally:
                    try:
                        if not result['success']:
                            discard_created_file(output_path, output_identity)
                        if not completed:
                            discard_created_file(evidence_path, evidence_identity)
                    except (OSError, SafeError):
                        result['cleanup_failed'] = True
                        raise SafeError('Private output cleanup failed; inspect files before using any credentials.') from None
                self.reply(200 if result['success'] else 502,
                           'Test OAuth validated and saved privately.' if result['success']
                           else 'Test OAuth could not be validated. No token was saved. Start a new session.')

        class LocalServer(HTTPServer):
            allow_reuse_address = False

            def get_request(self):
                connection, address = super().get_request()
                connection.settimeout(5)
                try:
                    return tls_context.wrap_socket(connection, server_side=True), address
                except (OSError, ssl.SSLError):
                    connection.close()
                    raise

            def handle_error(self, request, client_address):
                # Base server would print a traceback with potentially sensitive locals.
                result['done'] = True
                result['observation'] = {'result': 'local_handler_failed',
                                         'token_saved': 'unknown' if result['cleanup_failed'] else False,
                                         'private_output_requires_inspection': result['cleanup_failed'],
                                         'payment_requests': 0}

        with LocalServer((CALLBACK_HOST, CALLBACK_PORT), Handler) as server:
            server.timeout = 1
            print('Waiting for the authorized test seller callback on local loopback (maximum 5 minutes).', flush=True)
            while not result['done'] and time.monotonic() < deadline:
                server.handle_request()
        if result['observation']:
            print(json.dumps(result['observation'], sort_keys=True))
        if not result['success']:
            if result['cleanup_failed']:
                raise SafeError('OAuth could not be completed; inspect private output files before using credentials.')
            raise SafeError('Test OAuth was not completed; no validated token is available.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare_parser = commands.add_parser('prepare', help='Create a private session and print the authorization URL.')
    serve_parser = commands.add_parser('serve', help='Receive one authorized local callback and exchange a test code.')
    for command in (prepare_parser, serve_parser):
        command.add_argument('--credentials-file', type=Path, required=True)
        command.add_argument('--session', type=Path, required=True)
    prepare_parser.add_argument('--expected-seller-id', required=True)
    prepare_parser.add_argument('--redirect-uri', default=REDIRECT_URI,
                                help='Fixed local HTTPS callback, or temporary Cloudflare callback only after explicit human approval.')
    serve_parser.add_argument('--output', type=Path, required=True)
    serve_parser.add_argument('--evidence', type=Path)
    serve_parser.add_argument('--tls-cert-file', type=Path, required=True)
    serve_parser.add_argument('--tls-key-file', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'prepare':
            print(prepare(args.credentials_file, args.expected_seller_id, args.session,
                          args.redirect_uri))
        else:
            serve(args.credentials_file, args.session, args.output, args.evidence,
                  args.tls_cert_file, args.tls_key_file)
    except SafeError as error:
        parser.exit(1, str(error) + '\n')
    except (OSError, ValueError, UnicodeError, KeyError, TypeError):
        parser.exit(1, 'Local input, file, network, or response processing failed. No sensitive details are printed.\n')


if __name__ == '__main__':
    main()
