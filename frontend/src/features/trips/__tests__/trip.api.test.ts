import { describe, it, expect, vi, beforeEach } from 'vitest';
import { tripApi } from '../trip.api';

vi.mock('@/lib/axios', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

import { apiClient } from '@/lib/axios';
const mockGet = vi.mocked(apiClient.get);
const mockPost = vi.mocked(apiClient.post);
const mockPatch = vi.mocked(apiClient.patch);
const mockDelete = vi.mocked(apiClient.delete);

describe('tripApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('list', () => {
    it('calls GET /api/trips with page and size', async () => {
      mockGet.mockResolvedValue({ data: { content: [], totalPages: 0 } });
      await tripApi.list(2, 10);
      expect(mockGet).toHaveBeenCalledWith('/api/trips', { params: { page: 2, size: 10 } });
    });

    it('uses defaults page=0 size=20', async () => {
      mockGet.mockResolvedValue({ data: { content: [] } });
      await tripApi.list();
      expect(mockGet).toHaveBeenCalledWith('/api/trips', { params: { page: 0, size: 20 } });
    });

    it('returns unwrapped data', async () => {
      const payload = { content: [{ id: '1' }], totalPages: 1 };
      mockGet.mockResolvedValue({ data: payload });
      const result = await tripApi.list();
      expect(result).toEqual(payload);
    });
  });

  describe('get', () => {
    it('calls GET /api/trips/:id', async () => {
      mockGet.mockResolvedValue({ data: { id: 'abc' } });
      const result = await tripApi.get('abc');
      expect(mockGet).toHaveBeenCalledWith('/api/trips/abc');
      expect(result).toEqual({ id: 'abc' });
    });
  });

  describe('create', () => {
    it('calls POST /api/trips with body', async () => {
      const req = { name: 'Trip', city: 'Paris', startDate: '2026-01-01', endDate: '2026-01-05' };
      mockPost.mockResolvedValue({ data: { id: 'new', ...req } });
      const result = await tripApi.create(req);
      expect(mockPost).toHaveBeenCalledWith('/api/trips', req);
      expect(result.id).toBe('new');
    });
  });

  describe('update', () => {
    it('calls PATCH /api/trips/:id with confirmShorten=false by default', async () => {
      mockPatch.mockResolvedValue({ data: { id: '1' } });
      await tripApi.update('1', { name: 'Renamed' });
      expect(mockPatch).toHaveBeenCalledWith('/api/trips/1', { name: 'Renamed' }, { params: { confirmShorten: false } });
    });

    it('passes confirmShorten=true when specified', async () => {
      mockPatch.mockResolvedValue({ data: { id: '1' } });
      await tripApi.update('1', { name: 'X' }, true);
      expect(mockPatch).toHaveBeenCalledWith('/api/trips/1', { name: 'X' }, { params: { confirmShorten: true } });
    });
  });

  describe('delete', () => {
    it('calls DELETE /api/trips/:id', async () => {
      mockDelete.mockResolvedValue({});
      await tripApi.delete('xyz');
      expect(mockDelete).toHaveBeenCalledWith('/api/trips/xyz');
    });
  });

  describe('addItem', () => {
    it('calls POST /api/trips/:tripId/days/:dayId/items', async () => {
      const req = { destinationRef: 'ref-1', title: 'Museum' };
      mockPost.mockResolvedValue({ data: { id: 'item-1' } });
      await tripApi.addItem('trip-1', 'day-1', req);
      expect(mockPost).toHaveBeenCalledWith('/api/trips/trip-1/days/day-1/items', req);
    });
  });

  describe('updateItem', () => {
    it('calls PATCH /api/trips/:tripId/items/:itemId', async () => {
      mockPatch.mockResolvedValue({ data: { id: 'item-1' } });
      await tripApi.updateItem('trip-1', 'item-1', { sortOrder: 2 });
      expect(mockPatch).toHaveBeenCalledWith('/api/trips/trip-1/items/item-1', { sortOrder: 2 });
    });
  });

  describe('deleteItem', () => {
    it('calls DELETE /api/trips/:tripId/items/:itemId', async () => {
      mockDelete.mockResolvedValue({});
      await tripApi.deleteItem('trip-1', 'item-1');
      expect(mockDelete).toHaveBeenCalledWith('/api/trips/trip-1/items/item-1');
    });
  });
});
