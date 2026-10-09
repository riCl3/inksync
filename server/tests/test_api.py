"""API behavior tests — focused on failure scenarios, not happy-path counts:

- auth: duplicate registration, wrong credentials, missing/expired session
- idempotency: replayed POST cannot duplicate a note
- concurrency: stale base_version → 409 with server state, never a silent overwrite
- isolation: user B gets 404 on user A's note (existence not even observable)
- rate limiting: brute force gets throttled
"""

from app import deps

EMAIL_A = "alice@example.com"
EMAIL_B = "bob@example.com"
PASSWORD = "correct-horse-battery"


def register(client, email=EMAIL_A, password=PASSWORD):
    res = client.post("/auth/register", json={"email": email, "password": password})
    assert res.status_code == 201, res.text
    return res.json()


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def make_note_id(n: int) -> str:
    return f"00000000-0000-0000-0000-00000000000{n}"


# --- auth -----------------------------------------------------------------


def test_register_returns_token_and_me_works(client):
    data = register(client)
    assert data["token_type"] == "bearer"
    me = client.get("/auth/me", headers=auth_headers(data["access_token"]))
    assert me.status_code == 200
    assert me.json()["email"] == EMAIL_A


def test_duplicate_registration_conflicts(client):
    register(client)
    res = client.post("/auth/register", json={"email": EMAIL_A, "password": PASSWORD})
    assert res.status_code == 409


def test_wrong_password_and_unknown_email_are_indistinguishable(client):
    register(client)
    bad_pw = client.post("/auth/login", json={"email": EMAIL_A, "password": "wrong-pass-123"})
    unknown = client.post("/auth/login", json={"email": "nobody@example.com", "password": "x" * 8})
    assert bad_pw.status_code == unknown.status_code == 401
    assert bad_pw.json() == unknown.json()  # no user oracle


def test_requests_without_token_are_unauthorized(client):
    assert client.get("/notes").status_code == 401
    assert client.post("/notes", json={}).status_code == 401
    assert client.get("/auth/me").status_code == 401


def test_garbage_token_is_rejected(client):
    res = client.get("/notes", headers=auth_headers("not.a.jwt"))
    assert res.status_code == 401


def test_auth_is_rate_limited(client):
    for _ in range(deps.AUTH_LIMIT):
        client.post(
            "/auth/login", json={"email": EMAIL_A, "password": "wrong-pass-123"}
        )
    res = client.post("/auth/login", json={"email": EMAIL_A, "password": "wrong-pass-123"})
    assert res.status_code == 429


# --- notes: idempotency ---------------------------------------------------


def test_replayed_create_returns_existing_note_without_duplicating(client):
    token = register(client)["access_token"]
    h = auth_headers(token)
    body = {"id": make_note_id(1), "title": "Once", "content": "only once"}

    first = client.post("/notes", json=body, headers=h)
    assert first.status_code == 201

    replay = client.post("/notes", json=body, headers=h)
    assert replay.status_code == 200
    assert replay.json()["id"] == first.json()["id"]

    listing = client.get("/notes", headers=h)
    assert len(listing.json()) == 1


# --- notes: optimistic concurrency ---------------------------------------


def test_stale_update_conflicts_and_returns_server_state(client):
    token = register(client)["access_token"]
    h = auth_headers(token)
    note_id = make_note_id(2)
    client.post(
        "/notes",
        json={"id": note_id, "title": "v1", "content": ""},
        headers=h,
    )

    # Device 1 applies its change on top of v1 → server becomes v2
    ok = client.put(
        f"/notes/{note_id}",
        json={"title": "Device A edit", "base_version": 1},
        headers=h,
    )
    assert ok.status_code == 200
    assert ok.json()["version"] == 2

    # Device 2 still holds v1 → its update must NOT silently overwrite
    stale = client.put(
        f"/notes/{note_id}",
        json={"title": "Device B edit", "base_version": 1},
        headers=h,
    )
    assert stale.status_code == 409
    detail = stale.json()["detail"]
    assert detail["reason"] == "version_conflict"
    assert detail["current"]["title"] == "Device A edit"  # server state exposed
    assert detail["current"]["version"] == 2

    # And the data was actually preserved
    assert client.get(f"/notes/{note_id}", headers=h).json()["title"] == "Device A edit"


def test_fresh_update_succeeds_and_bumps_version(client):
    token = register(client)["access_token"]
    h = auth_headers(token)
    note_id = make_note_id(3)
    client.post("/notes", json={"id": note_id, "title": "a"}, headers=h)
    res = client.put(
        f"/notes/{note_id}", json={"title": "b", "base_version": 1}, headers=h
    )
    assert res.status_code == 200
    assert res.json()["version"] == 2
    assert res.json()["title"] == "b"


# --- notes: ownership isolation ------------------------------------------


def test_other_users_note_is_invisible_and_untouchable(client):
    token_a = register(client, EMAIL_A)["access_token"]
    token_b = register(client, EMAIL_B)["access_token"]
    note_id = make_note_id(4)
    client.post(
        "/notes",
        json={"id": note_id, "title": "Alice private"},
        headers=auth_headers(token_a),
    )

    # Not in B's list
    assert all(n["id"] != note_id for n in client.get("/notes", headers=auth_headers(token_b)).json())

    # Direct access → 404, identical to a truly nonexistent id:
    # B cannot even learn that this note exists.
    assert client.get(f"/notes/{note_id}", headers=auth_headers(token_b)).status_code == 404
    assert client.get("/notes/11111111-1111-1111-1111-111111111111", headers=auth_headers(token_b)).status_code == 404
    assert (
        client.put(
            f"/notes/{note_id}",
            json={"title": "hijack", "base_version": 1},
            headers=auth_headers(token_b),
        ).status_code
        == 404
    )
    assert client.delete(f"/notes/{note_id}", headers=auth_headers(token_b)).status_code == 404
    # Alice's note untouched
    assert (
        client.get(f"/notes/{note_id}", headers=auth_headers(token_a)).json()["title"]
        == "Alice private"
    )


def test_cannot_claim_an_id_owned_by_another_user(client):
    token_a = register(client, EMAIL_A)["access_token"]
    token_b = register(client, EMAIL_B)["access_token"]
    note_id = make_note_id(5)
    client.post("/notes", json={"id": note_id}, headers=auth_headers(token_a))
    res = client.post("/notes", json={"id": note_id}, headers=auth_headers(token_b))
    assert res.status_code == 409


# --- delete ----------------------------------------------------------------


def test_delete_removes_note_from_listing(client):
    token = register(client)["access_token"]
    h = auth_headers(token)
    note_id = make_note_id(6)
    client.post("/notes", json={"id": note_id}, headers=h)

    assert client.delete(f"/notes/{note_id}", headers=h).status_code == 204
    assert client.get(f"/notes/{note_id}", headers=h).status_code == 404
    assert client.get("/notes", headers=h).json() == []
