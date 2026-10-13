# TicketSwap Mobile Assignment

A native Android app (Kotlin, Jetpack Compose) built on the provided GraphQL backend. You can browse and search events, choose tickets, pay, and get tickets with a barcode that can be scanned.

Package: `com.mashkov.ticketswap`

## The idea

A simple ticket buyer. Instead of using every API, I built one full flow and tried to make it solid:

1. Login (`first` / `user`)
2. Events list with search. Upcoming events come first, past events are dimmed.
3. Event details with a "- N +" stepper for each ticket type and a cart bar.
4. Cart and payment, then a confirmation screen with a barcode for every ticket.

Things I added on top:

- **Real barcodes.** The backend returns a 13-digit `barcode`. ZXing converts it to bars and I draw them on a Compose Canvas, black on white, so another phone can scan the screen.
- **Error handling.** The backend can fail on purpose (`ERROR_RATE`, `MIN_DELAY_MS`), so every request has loading and error states with a retry button. Buttons are locked while a request is running, and a failed request never empties the cart.
- **Expired sessions.** The server keeps sessions in memory, so after a restart the saved token no longer works. When the server says the token is invalid, the app clears it and goes back to the login screen.

## How to run

Backend:

```
npm install
npm start        # GraphiQL: http://localhost:4000/graphql
```

App:

- Open the project in a recent Android Studio, sync Gradle, and run it on an emulator or a device.
- The server address is `GRAPHQL_URL` in `app/build.gradle.kts`. It is set to `http://localhost:4000/graphql`, so forward the port once per emulator session: `adb reverse tcp:4000 tcp:4000` (it also works for a device connected by USB). Without it, an emulator can use `http://10.0.2.2:4000/graphql` instead.
- The backend uses plain HTTP, so cleartext traffic is allowed for local hosts only (`res/xml/network_security_config.xml`).
- Demo data: user `first` / `user`, card `4242 4242 4242 4242`.

## Architecture

One Gradle module, MVVM. Screens show a `StateFlow` from a ViewModel, ViewModels call repositories, and repositories call the backend with Apollo Kotlin.

```
core/         AppError, AppResult, executeCatching (turns Apollo failures into AppError)
data/
  auth/       TokenStorage (DataStore), AuthRepository, SessionManager
  events/     EventsRepository and models
  cart/       CartRepository (the single source of truth for the cart)
ui/           login, events, details, cart (a screen and a ViewModel each), navigation
AppContainer  hand-written dependency container
```

GraphQL operations are in `app/src/main/graphql/`. Apollo generates the models from `schema.graphqls`. It is the provided `schema.graphql`, renamed because Apollo expects that extension for schema files.

## Decisions

- **Manual DI instead of Hilt.** The app is small, so one container is enough.
- **Errors are values.** Repositories return `AppResult<T>` with a typed `AppError`. The UI turns errors into text in one place.
- **The server owns the cart.** `CartRepository` keeps a copy in a `StateFlow` and replaces it with the server's answer after each call. Calls run one at a time (`Mutex`), so an old response cannot overwrite a newer one.
- **A stepper instead of "Add" buttons.** The backend keeps one cart line per unit and removes one unit per call, so a stepper fits well.
- **Stock is updated locally.** The backend takes a unit out of stock when you add it and puts it back when you remove it, but the cart response has no stock numbers. So I change the number locally after a successful call. It is fully reloaded when the screen opens or on retry.
- **Search runs on the server** with a 300 ms delay. A new query cancels the old request. An empty field shows all events, because the backend returns nothing for an empty search.
- **Past events are shown.** Most events in the sample data are in the past, so hiding them would leave almost nothing. They come after upcoming events and cannot be added to the cart.
- **Event names on bought tickets.** `checkout` does not say which event a ticket belongs to. The backend builds the tickets by going through the cart in order, so I match them with a copy of the cart taken before paying. If the counts differ, the names are left out.
- **Money is stored in cents** and only converted to a decimal for display.
- **The token is stored in Preferences DataStore** behind a small `TokenStorage` interface.

## Notes on the backend

I did not change the backend. These are things I noticed:

- Errors come back as HTTP 200 with `error { text }`, and there are no error codes. To detect a bad session I match the message text (in one place, `serverError()`).
- The token is a mutation argument, not a header, and `authenticatedUser` is a mutation.
- Adding to the cart takes stock, but there is no timeout. Items left in a cart stay unavailable until the server restarts.
- `checkout` is not idempotent. If the payment succeeds but the response is lost, a retry says the cart is empty.
- Cart lines have no stock info, and bought tickets have no event info.

## Tests

9 JVM unit tests (JUnit, MockK, coroutines-test). They cover cart totals and grouping, mapping of token errors, card number formatting, the payment flow (success, failure keeps the cart, double tap sends one request) and stock handling on the event screen.

Run them with `./gradlew :app:testDebugUnitTest`.

## Limitations and next steps

- Encrypt the token with the Android Keystore.
- Use `saveCreditCard` and `ticketWallet`: a saved card and a "My tickets" screen. Right now tickets are shown only after payment.
- A "tickets for sale" feed with `ticketsForPurchase`.
- Handle a lost response on checkout, for example by checking the wallet.
- Pull-to-refresh, `strings.xml`, UI tests.

## AI use

I built this together with Claude (Anthropic), one step at a time: backend analysis, scope, architecture, then screen by screen and tests. I ran and debugged everything myself and made the product decisions. A selection of the prompts is in `PROMPTS.txt`.

Time spent: about 8 hours.
