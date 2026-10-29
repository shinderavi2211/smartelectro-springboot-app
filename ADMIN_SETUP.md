# Admin account setup

Admin access is assigned through the existing `users.role` field. There is no default admin account or public self-promotion endpoint. Ordinary registrations continue to receive the `USER` role.

To authorize an existing account, an operator with trusted database access should:

1. Confirm the intended account with `SELECT id, email, role FROM users WHERE email = '<exact-account-email>';`.
2. Verify the account belongs to the intended administrator and back up the database according to the deployment's normal procedure.
3. Promote only that account with `UPDATE users SET role = 'ADMIN' WHERE email = '<exact-account-email>' AND role = 'USER';`.
4. Confirm exactly one row changed, then sign out and back in so the session receives the `ROLE_ADMIN` authority.

Do not expose database credentials or run this against a customer account without explicit authorization. Existing `ADMIN` roles are retained. The dashboard and `/api/admin/**` endpoints require `ROLE_ADMIN`.
