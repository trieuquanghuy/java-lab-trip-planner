// Augment the Window interface for test-only globals injected via addInitScript
interface Window {
  __TEST_ACCESS_TOKEN__?: string;
}
