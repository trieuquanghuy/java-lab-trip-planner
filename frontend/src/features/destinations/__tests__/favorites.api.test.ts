import { describe, it, expect, vi, beforeEach } from 'vitest';
import { favoritesApi } from '../favorites.api';

vi.mock('@/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

import { apiClient } from '@/lib/axios';
const mockApiClient = vi.mocked(apiClient);

describe('favoritesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('list', () => {
    it('calls GET /api/favorites and returns response data', async () => {
      const payload = { items: [{ destinationRef: 'otm:1', createdAt: '2024-01-01T00:00:00Z' }] };
      mockApiClient.get.mockResolvedValue({ data: payload });

      const result = await favoritesApi.list();

      expect(mockApiClient.get).toHaveBeenCalledWith('/api/favorites');
      expect(result).toEqual(payload);
    });
  });

  describe('add', () => {
    it('calls POST /api/favorites with correct body and returns data', async () => {
      const item = { destinationRef: 'otm:42', createdAt: '2024-06-01T00:00:00Z' };
      mockApiClient.post.mockResolvedValue({ data: item });

      const result = await favoritesApi.add('otm:42');

      expect(mockApiClient.post).toHaveBeenCalledWith('/api/favorites', { destinationRef: 'otm:42' });
      expect(result).toEqual(item);
    });
  });

  describe('remove', () => {
    it('calls DELETE /api/favorites/{ref} with the correct URL', async () => {
      mockApiClient.delete.mockResolvedValue({ status: 204 });

      await favoritesApi.remove('otm:99');

      expect(mockApiClient.delete).toHaveBeenCalledWith('/api/favorites/otm:99');
    });
  });
});
