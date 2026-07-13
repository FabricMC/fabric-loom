# Microsoft authentication

Set the empty `net.fabricmc.loom.util.Constants.MICROSOFT_CLIENT_ID` placeholder to the application
ID used for Microsoft authentication.

To create an ID:

1. Open [Microsoft Entra](https://entra.microsoft.com/) and go to **Identity > Applications > App registrations**.
2. Create a registration that supports personal Microsoft accounts.
3. Under **Authentication > Advanced settings**, enable **Allow public client flows**.
4. Copy the **Application (client) ID** from the application's overview page.

Run `microsoftLogin` once to sign in. Loom stores the encrypted account in
`<GRADLE_USER_HOME>/caches/fabric-loom/microsoft-auth.json`, so it can be reused by every project.
The `runClient` task refreshes the account and passes a Minecraft access token to the game.
If authentication fails, the game still starts without an authenticated account.

Run `microsoftLogout` to delete the locally stored account and its platform-protected key.
Each Gradle user home has a separate platform key, so logging out does not affect credentials
stored under another Gradle user home. Logout is local-only because this personal-account flow
has no supported per-token revocation endpoint; see Microsoft's
[refresh-token documentation](https://learn.microsoft.com/en-us/entra/identity-platform/refresh-tokens).
