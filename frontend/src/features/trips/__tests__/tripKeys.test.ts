import { describe, it, expect } from 'vitest';
import { tripKeys } from '../trip.hooks';

describe('tripKeys', () => {
  it('generates base key', () => {
    expect(tripKeys.all).toEqual(['trips']);
  });

  it('generates lists key', () => {
    expect(tripKeys.lists()).toEqual(['trips', 'list']);
  });

  it('generates list key with page', () => {
    expect(tripKeys.list(0)).toEqual(['trips', 'list', 0]);
    expect(tripKeys.list(3)).toEqual(['trips', 'list', 3]);
  });

  it('generates details key', () => {
    expect(tripKeys.details()).toEqual(['trips', 'detail']);
  });

  it('generates detail key with id', () => {
    expect(tripKeys.detail('abc-123')).toEqual(['trips', 'detail', 'abc-123']);
  });

  it('list keys are subsets of lists key (for invalidation)', () => {
    const listKey = tripKeys.list(0);
    const listsKey = tripKeys.lists();
    // list key starts with lists key
    expect(listKey.slice(0, listsKey.length)).toEqual(listsKey);
  });

  it('detail keys are subsets of details key (for invalidation)', () => {
    const detailKey = tripKeys.detail('x');
    const detailsKey = tripKeys.details();
    expect(detailKey.slice(0, detailsKey.length)).toEqual(detailsKey);
  });
});
