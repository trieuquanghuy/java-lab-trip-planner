<!-- generated-by: gsd-doc-writer -->
# Trip Planner — User Guide

Welcome to **Trip Planner** — a web app for travelers who want to build detailed, day-by-day itineraries. Search for destinations around the world, browse local attractions, save favorites, and organize every stop of your journey in one place.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Discovering Destinations](#discovering-destinations)
3. [Planning a Trip](#planning-a-trip)
4. [Managing Trips](#managing-trips)
5. [Favorites](#favorites)
6. [Map View](#map-view)
7. [Account](#account)
8. [Tips & Tricks](#tips--tricks)
9. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites

Before you begin, make sure you have **Docker Desktop** installed on your computer. Docker Desktop is free and available for macOS, Windows, and Linux.

- Download Docker Desktop: <https://www.docker.com/products/docker-desktop>
- Minimum version: Docker Desktop 4.30+ (or Docker Engine 26+ with Docker Compose 2.20+)

### Starting the App

1. Open a terminal and navigate to the project folder.
2. Run the following command:

   ```bash
   docker compose up
   ```

3. Wait for all services to start. On the **first launch**, the backend discovery service (Eureka) takes about **60 seconds** to warm up — this is normal. You'll see log output as each service comes online.

> **Note:** If you see connection errors in the first minute, simply wait. The app will become fully functional once all services have registered with each other.

### Accessing the App

Once the services are running, open your browser and go to:

```
http://localhost:5173
```

You'll land on the home page where you can start searching for destinations right away. Some features (like adding to a trip or saving favorites) require you to be logged in.

### Registering and Verifying Your Email

1. Click **Sign up** in the top navigation bar.
2. Enter your email address and a password (minimum 8 characters).
3. Click **Sign Up** — you'll be redirected to a confirmation screen.
4. Open **Mailhog** (the local email inbox used in development) at:

   ```
   http://localhost:8025
   ```

5. Find the verification email and click the link inside it.
6. You'll be redirected back to the app with a confirmation message: *"Email verified — please log in."*
7. Log in with your email and password.

> **Note:** Mailhog is a local email testing tool — no real emails are sent during development. All verification emails appear at `http://localhost:8025`.

---

## Discovering Destinations

### Searching for a City or Country

The search bar appears prominently on the home page.

1. Click the search bar and start typing a city name (e.g., *"Tokyo"*) or a country name (e.g., *"Japan"*).
2. As you type, suggestions appear automatically — the search is **debounced**, meaning it waits briefly after you stop typing before firing, so results feel snappy and don't flicker.
3. Click a suggestion to load attractions for that location.

### Browsing Attractions

After selecting a destination, a grid of attraction cards appears. Each card shows:

- **Photo** — a thumbnail image of the attraction
- **Name** — the place's name
- **Category** — the type of attraction (museum, park, restaurant, etc.)
- **Rating** — a star rating out of 5

Scroll down to explore all available attractions for the area.

### Viewing Destination Details

Click any attraction card to open its **detail view**. Here you'll find:

- **Photo carousel** — swipe or click through multiple photos
- **Name** and **category badge**
- **Rating**
- **Address** with a map pin icon (📍)
- **Opening hours** — or *"Opening hours not available"* if the venue hasn't provided them
- **Website link** — opens the venue's official site in a new tab
- **Add to Trip** button — visible only when you're logged in (see [Planning a Trip](#planning-a-trip))

---

## Planning a Trip

### Creating Your First Trip

1. Log in to your account.
2. Click **Create Trip** (available in the navigation or on the Trips page).
3. A three-step wizard appears:
   - **Step 1 — Name your trip:** Enter a memorable name, such as *"Tokyo 2026"*. Names can be up to 120 characters.
   - **Step 2 — When are you going?** Optionally set a **Start date** and **End date**. You can skip this and set dates later.
   - **Step 3 — Ready to plan!** Review your trip details and click **Create Trip** to confirm.
4. You'll be taken directly to your new trip's planning board.

> **Note:** Dates are optional at creation time. You can always add or update them later from the trip detail page.

### Setting Trip Dates

If you set (or update) a start and end date on a trip, the app **automatically creates the right number of day columns** for you — one column per day between the two dates.

For example, a trip from June 1–5 will have **5 day columns**: Day 1 · Jun 1, Day 2 · Jun 2, and so on.

To update dates on an existing trip, edit the trip name/dates field at the top of the trip detail page.

### Adding Destinations to Your Itinerary

There are two ways to add an attraction to your trip:

**From the destination detail page:**
1. Open a destination's detail view (search → click a card).
2. Click **Add to Trip**.
3. A dropdown appears listing all your trips and their day columns.
4. Select the day you want to add the destination to.

**From the trip planning board:**
Each day column has an **Add to Trip** control you can use to search and add destinations directly.

### Reordering Items (Drag and Drop)

Within any day column, you can **drag and drop** items to change their order:

1. Click and hold on an item card.
2. Drag it up or down within the column.
3. Release to drop it in the new position.

The travel times between stops will automatically recalculate after you reorder.

### Moving Items Between Days

You can also **move an item to a different day** by dragging it across columns:

1. Click and hold on an item card.
2. Drag it horizontally to another day column — the target column will highlight when you hover over it.
3. Release to drop the item into that day.

> **Note:** You need enough horizontal space to see multiple day columns side by side. On smaller screens, try scrolling the board horizontally.

### Viewing Weather Forecasts

When your trip has **future dates** set, each day column displays a weather card near the top showing:

- **Weather icon** — an emoji representing the day's conditions (e.g., ☀️, 🌧️, ⛅)
- **High temperature** — e.g., `28°`
- **Low temperature** — e.g., `/ 18°`
- **Precipitation** — e.g., `💧 4.2mm` (shown only when rain is expected)

> **Note:** Weather forecasts are only shown for dates **within the next 16 days**. If your trip is further in the future, or dates are not set, the weather cards will not appear.

### Checking Travel Times Between Stops

Between consecutive items in a day column, the app shows an estimated travel segment:

```
🚗 12 min · 3.2 km
```

This represents the driving time and distance between two stops in the order they appear in the day. If routing data is unavailable for a pair of locations, it shows:

```
🚗 Travel time unavailable
```

Travel times update automatically when you reorder or move items.

---

## Managing Trips

### Your Trips Page

Click **Trips** in the navigation bar to see all your trips displayed in a grid. Each card shows the trip name and dates (if set). Click any card to open the full planning board for that trip.

### Duplicating a Trip

To create a copy of an existing trip:

1. Open the trip you want to duplicate.
2. Click the **Duplicate** button (copy icon) in the trip header.
3. A new trip is created named **"Copy of {original name}"**.

> **Note:** Duplicated trips have their **dates reset** — you'll need to set new travel dates if required. All itinerary items are preserved.

### Sharing a Trip (Public Read-Only Link)

You can share any trip with anyone — even people who don't have an account — using a public read-only link.

1. Open the trip you want to share.
2. Click the **Share** button (🔗 icon) in the trip header.
3. A popover appears saying *"Share this trip."*
4. If this is the first time sharing, a link is generated automatically.
5. Click the link box or **Copy Link** to copy the URL to your clipboard.
6. Send the link to anyone — they can view the full itinerary and map without logging in.

> **Note:** Recipients of a shared link see a **read-only view** — they can browse days, items, and the map, but cannot edit anything.

### Revoking a Share Link

If you want to stop sharing a trip:

1. Open the trip and click the **Share** button.
2. In the popover, click **Revoke**.
3. The link is deactivated immediately — anyone who tries to use the old link will see *"This link may have been revoked or is invalid."*

---

## Favorites

### Saving Destinations

When browsing attraction cards or viewing a destination's detail page, you'll see a **heart icon** (♥).

- Click the heart to **save** a destination to your favorites.
- Click it again to **unsave** it.

> **Note:** You must be logged in to save favorites. The heart icon is only interactive for authenticated users.

### Viewing and Managing Favorites

Click **Favorites** in the navigation bar to see all your saved destinations in a grid.

- If you have no favorites yet, you'll see the message *"No favorites yet"* and a **Discover Destinations** button to start exploring.
- Each saved destination shows its card with photo, name, category, and rating.
- To remove a destination, click the **heart icon** on the card. The card fades out with a smooth animation and disappears from the grid.

> **Tip:** The `aria-label` for the remove button is *"Remove from favorites"* — useful if you're using a screen reader or keyboard navigation.

---

## Map View

### Opening the Map

On a trip's planning board, click the **Map** button (🗺️ icon) in the trip header to open the interactive map panel. Click it again (or the **✕** close button) to dismiss it.

### What's Shown on the Map

The map displays **all destinations across all days** of the trip as map markers. Each marker is labeled with the destination's name. This gives you a geographic overview of your entire itinerary at a glance.

The shared trip view also includes a map showing all the same destinations in read-only mode.

> **Note:** A destination only appears on the map after its geographic coordinates have been loaded. If a marker seems missing, try scrolling through the day columns first to trigger the data load.

---

## Account

### Signing Up

1. Click **Sign up** in the navigation bar.
2. Fill in your **email address** and a **password** (minimum 8 characters).
3. Click **Sign Up**.
4. You'll be redirected to a screen prompting you to check your email.

Passwords must be at least 8 characters long. If you enter an invalid email format or a weak password, you'll see an inline error message.

### Email Verification

After signing up, you **must verify your email** before you can log in.

1. Go to `http://localhost:8025` (Mailhog).
2. Open the verification email from Trip Planner.
3. Click the verification link.
4. You'll be returned to the app and shown a success message.

Verification links can expire. If yours has expired, you'll see *"Verification link expired"* — in that case, sign up again or contact your administrator.

### Logging In and Out

**To log in:**
1. Click **Log in** in the navigation bar.
2. Enter your verified email and password.
3. Click **Log in** — you'll be taken to the home page.

**To log out:**
1. Click **Log out** in the navigation bar (visible when you're logged in).
2. Your session ends and you're returned to the home page.

Your trips and favorites are saved to your account and will be there the next time you log in.

---

## Tips & Tricks

### Best Practices for Planning

- **Name trips clearly** — include the destination and year (e.g., *"Paris Oct 2025"*) so they're easy to identify on your Trips page.
- **Set dates early** — adding dates right away lets the app auto-create day columns and show weather forecasts.
- **Use Favorites as a wishlist** — save interesting places while browsing, then add them to a trip when you're ready to plan.
- **Check the map regularly** — opening the map view helps you spot when stops are geographically scattered and might need reordering.

### Understanding Weather Display

- Weather is powered by a 16-day forecast feed. Only days that fall **within the next 16 days from today** will show weather cards.
- The temperature is shown in **Celsius**.
- The rain indicator (`💧 X.Xmm`) only appears when measurable precipitation is expected — no indicator means a dry day is forecast.
- Weather data is anchored to the **geographic location of your first added destination** in the trip.

### Travel Time Notes

- Travel times use **road routing** and represent estimated **driving time**.
- Times are shown **between consecutive items** in each day column, in the order they appear.
- If you reorder items by dragging, travel time estimates automatically recalculate.
- If a segment shows *"🚗 Travel time unavailable"*, it typically means the routing service (OSRM) could not find a valid driving route between those two points — this can happen for remote locations or when coordinates are imprecise.

---

## Troubleshooting

### App Won't Load / Services Not Responding (Cold Start)

**Symptom:** The app shows errors or blank content right after `docker compose up`.

**Cause:** The backend service discovery layer (Eureka) takes approximately **60 seconds** to fully initialize on first start. Until it's ready, microservices can't find each other.

**Fix:** Wait 60 seconds and refresh the browser. Everything should load normally once all services have registered.

---

### Verification Email Not Received

**Symptom:** After signing up, you check your email inbox but don't see a verification link.

**Cause:** In the development environment, emails are **not sent to real inboxes** — they go to the local Mailhog inbox instead.

**Fix:**
1. Open `http://localhost:8025` in your browser.
2. You should see the verification email listed there.
3. Click the email and then click the verification link inside it.

---

### Weather Not Showing

**Symptom:** A trip with dates set shows no weather cards in the day columns.

**Cause:** Weather forecasts are only available for days **within the next 16 days**. Trips with dates further in the future, or trips with no dates set, will not show weather.

**Fix:**
- If you're testing, temporarily set trip dates to the next 1–7 days to confirm weather is loading.
- For trips more than 16 days away, weather will appear automatically as the travel date approaches.
- Ensure at least one destination has been added to the trip — the weather is geo-anchored to the first destination's location.

---

### Travel Time Shows "Unavailable"

**Symptom:** Between two stops in a day, you see *"🚗 Travel time unavailable"* instead of a time estimate.

**Cause:** The routing service (OSRM) could not calculate a driving route between those two destinations. This can happen if:
- The destination has imprecise or missing coordinates.
- The two locations are on different islands or continents where driving isn't possible.
- The OSRM service is still starting up after `docker compose up`.

**Fix:**
- Wait a minute and refresh if the app was just started.
- Try reordering the items in the day to see if a different pair of stops resolves.
- Driving routes between locations separated by water (e.g., island-to-island) will always show as unavailable — this is expected behavior.

---

*For developer setup, API reference, and architecture documentation, see the [`docs/`](.) folder.*
