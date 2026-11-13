import urllib.request, json, base64, hmac, hashlib, time

def get_token():
    def b64url(b): return base64.urlsafe_b64encode(b).decode('utf-8').rstrip('=')
    secret = base64.b64decode('404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970')
    now = int(time.time())
    header = {'alg': 'HS256', 'typ': 'JWT'}
    payload = {'sub': 'admin', 'iat': now, 'exp': now + 86400}
    p1 = b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    p2 = b64url(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    sig = hmac.new(secret, f'{p1}.{p2}'.encode('utf-8'), hashlib.sha256).digest()
    return f'{p1}.{p2}.{b64url(sig)}'

token = get_token()
req = urllib.request.Request('http://localhost:8082/api/medecins', headers={'Authorization': f'Bearer {token}'})
with urllib.request.urlopen(req) as resp:
    meds = json.loads(resp.read().decode('utf-8'))['items']

req2 = urllib.request.Request('http://localhost:8082/api/actions', headers={'Authorization': f'Bearer {token}'})
with urllib.request.urlopen(req2) as resp:
    actions = json.loads(resp.read().decode('utf-8'))['items']

act_map = {a['medecin']['id']: a for a in actions}

meds_sorted = sorted(meds, key=lambda m: (m.get('scoreUrgence') or 0.0), reverse=True)
for i, m in enumerate(meds_sorted, 1):
    a = act_map.get(m['id'])
    act_type = a.get('actionRecommandee') if a else 'AUCUNE'
    comm = a.get('commercial') if a else '-'
    ech = a.get('dateEcheance') if a else '-'
    print(f"{i}|{m.get('nom')} {m.get('prenom')}|{m.get('specialite')}|{m.get('organisme')}|{m.get('caMensuelMoyen')}|{m.get('caMois')}|{m.get('referenceCa')}|{m.get('variationMixteSur100')}|{m.get('intervalleEffectif')}|{m.get('joursSansActivite')}|{m.get('scoreValeur')}|{m.get('segment')}|{m.get('scoreSilence')}|{m.get('scoreRisque')}|{m.get('scoreUrgence')}|{m.get('statutPilotage')}|{act_type}|{comm}|{ech}")
