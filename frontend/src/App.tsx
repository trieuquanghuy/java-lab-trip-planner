export default function App() {
  return (
    <div className="min-h-screen flex flex-col">
      {/* Header slot — empty in P0; P7 lands brand + nav + auth controls */}
      <header className="border-b border-border">
        {/* intentionally empty in Phase 0 */}
      </header>

      {/* Main slot — Phase 0 = single landing element; P7+ = <Routes/> */}
      <main className="flex-1 container mx-auto px-4 py-8 md:py-12">
        <h1 className="text-2xl font-semibold tracking-tight">Trip Planner</h1>
        <p className="mt-2 text-base text-muted-foreground">
          Your itinerary, day by day.
        </p>
      </main>

      {/* Footer slot — empty in P0; P9 polish may add */}
      <footer className="border-t border-border">
        {/* intentionally empty in Phase 0 */}
      </footer>
    </div>
  );
}
