import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchNearby, fetchDestinationDetail } from '../destinations.api';

vi.mock('@/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

import { apiClient } from '@/lib/axios';
const mockGet = vi.mocked(apiClient.get);

describe('destinations.api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('fetchNearby', () => {
    it('calls GET /api/destinations with params', async () => {
      mockGet.mockResolvedValue({ data: { items: [] } });
      await fetchNearby(48.8, 2.3, 15000, 10);
      expect(mockGet).toHaveBeenCalledWith('/api/destinations', {
        params: { lat: 48.8, lng: 2.3, radius: 15000, limit: 10 },
      });
    });

    it('uses default radius=20000 and limit=20', async () => {
      mockGet.mockResolvedValue({ data: { items: [] } });
      await fetchNearby(40.0, -74.0);
      expect(mockGet).toHaveBeenCalledWith('/api/destinations', {
        params: { lat: 40.0, lng: -74.0, radius: 20000, limit: 20 },
      });
    });

    it('returns unwrapped data', async () => {
      const payload = { items: [{ providerRef: 'x', name: 'X' }] };
      mockGet.mockResolvedValue({ data: payload });
      const result = await fetchNearby(0, 0);
      expect(result).toEqual(payload);
    });
  });

  describe('fetchDestinationDetail', () => {
    it('calls GET /api/destinations/:providerRef', async () => {
      mockGet.mockResolvedValue({ data: { providerRef: 'abc', name: 'ABC' } });
      await fetchDestinationDetail('abc');
      expect(mockGet).toHaveBeenCalledWith('/api/destinations/abc');
    });

    it('encodes special characters in providerRef', async () => {
      mockGet.mockResolvedValue({ data: {} });
      await fetchDestinationDetail('place/with spaces');
      expect(mockGet).toHaveBeenCalledWith('/api/destinations/place%2Fwith%20spaces');
    });

    it('returns unwrapped data', async () => {
      const detail = { providerRef: 'p1', name: 'Place 1', photos: [] };
      mockGet.mockResolvedValue({ data: detail });
      const result = await fetchDestinationDetail('p1');
      expect(result).toEqual(detail);
    });
  });
});
