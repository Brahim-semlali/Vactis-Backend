import urllib.request, json, base64, hmac, hashlib, time

def get_token():
    def b64url(b):
        return base64.urlsafe_b64encode(b).decode('utf-8').rstrip('=')
    secret = base64.b64decode('404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970')
    now = int(time.time())
    header = {'alg': 'HS256', 'typ': 'JWT'}
    payload = {'sub': 'admin', 'iat': now, 'exp': now + 86400}
    p1 = b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    p2 = b64url(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    sig = hmac.new(secret, f'{p1}.{p2}'.encode('utf-8'), hashlib.sha256).digest()
    p3 = b64url(sig)
    return f'{p1}.{p2}.{p3}'

token = get_token()

def call_api(endpoint, method='GET', body=None):
    url = f'http://localhost:8082{endpoint}'
    data = json.dumps(body).encode('utf-8') if body else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json'
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            text = resp.read().decode('utf-8')
            return json.loads(text) if text else {}
    except Exception as e:
        return {'error': str(e)}

print("1. Triggering recalculation sync...")
sync_res = call_api('/api/medecins/sync', method='POST')
print("Sync response:", sync_res)

print("\n2. Fetching medecins...")
meds_res = call_api('/api/medecins')
meds = meds_res.get('items', []) if isinstance(meds_res, dict) and 'items' in meds_res else meds_res
print(f"Total medecins count: {len(meds)}")

print("\n3. Fetching actions...")
actions_res = call_api('/api/actions')
actions = actions_res.get('items', []) if isinstance(actions_res, dict) and 'items' in actions_res else actions_res
print(f"Total actions count: {len(actions)}")

focus_names = ["rahmani", "ghazouani", "boukili"]
found = {}
for m in meds:
    nom_complet = f"{m.get('nom', '')} {m.get('prenom', '')}".lower()
    for fn in focus_names:
        if fn in nom_complet:
            found[fn] = m

print("\n--- FOCUS DOCTORS IN MEDECINS ---")
for k, m in found.items():
    print(f"Doctor: {m.get('nom')} {m.get('prenom')} (ID: {m.get('id')})")
    print(f"  CA Mensuel Moyen: {m.get('caMensuelMoyen')}")
    print(f"  Intervalle Effectif: {m.get('intervalleEffectif')}")
    print(f"  Score Valeur: {m.get('scoreValeur')} (Segment: {m.get('segment')})")
    print(f"  Score Urgence: {m.get('scoreUrgence')} (Risque: {m.get('risqueUrgence')})")
    print(f"  Statut Pilotage: {m.get('statutPilotage')}")
    print(f"  Rang Priorite: {m.get('rangPriorite')}")

print("\n--- ACTIONS FOR FOCUS DOCTORS ---")
for a in actions:
    med = a.get("medecin", {})
    nom_complet = f"{med.get('nom', '')} {med.get('prenom', '')}".lower()
    for fn in focus_names:
        if fn in nom_complet:
            print(f"Action for {med.get('nom')} {med.get('prenom')}:")
            print(f"  Type: {a.get('typeAction')}, Statut: {a.get('statut')}, Horizon: {a.get('horizonJours')}, Echeance: {a.get('dateEcheance')}, Perte: {a.get('perteEstimeeCa')}")

inactifs = [m for m in meds if m.get('statutPilotage') == 'INACTIF']
print(f"\nInactifs in Medecins: {len(inactifs)} ({[m.get('nom') for m in inactifs]})")

# Check if any inactive doctor has an action
inactif_ids = {m.get('id') for m in inactifs}
inactif_actions = [a for a in actions if a.get('medecin', {}).get('id') in inactif_ids]
print(f"Actions assigned to inactifs: {len(inactif_actions)}")

# Check lecture activite
activite_res = call_api('/api/activite')
print("\n--- LECTURE ACTIVITE ---")
if isinstance(activite_res, dict):
    print(f"Mois: {activite_res.get('mois')}, CA Total: {activite_res.get('caTotal')}, Nb Medecins Actifs: {activite_res.get('nombreMedecinsActifs')}")
