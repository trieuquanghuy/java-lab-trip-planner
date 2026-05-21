-- Seed data for trip-service (trip schema).
-- Run against local postgres:
--   docker exec -i $(docker ps -qf "name=postgres") psql -U postgres -d tripplanner < infra/seeds/seed-trips.sql
--
-- Prerequisites: test user must exist (email: test@tripplanner.local)
-- User ID: 4af20ff9-f859-40fe-b392-a02c8da8edc8

-- Clear existing seed data (idempotent re-run)
DELETE FROM trip.itinerary_items WHERE itinerary_day_id IN (
  SELECT id FROM trip.itinerary_days WHERE trip_id IN (
    SELECT id FROM trip.trips WHERE user_id = '4af20ff9-f859-40fe-b392-a02c8da8edc8'
  )
);
DELETE FROM trip.itinerary_days WHERE trip_id IN (
  SELECT id FROM trip.trips WHERE user_id = '4af20ff9-f859-40fe-b392-a02c8da8edc8'
);
DELETE FROM trip.trips WHERE user_id = '4af20ff9-f859-40fe-b392-a02c8da8edc8';

-- ============================================================
-- TRIPS
-- ============================================================

INSERT INTO trip.trips (id, user_id, name, start_date, end_date, cover_image_url, created_at, updated_at) VALUES
('a0000001-0000-0000-0000-000000000001', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Tokyo Adventure', '2026-06-10', '2026-06-15', 'https://images.unsplash.com/photo-1540959733332-eab4deabeeaf?w=400&h=200&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),
('a0000001-0000-0000-0000-000000000002', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Paris Food Tour', '2026-07-01', '2026-07-05', 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=400&h=200&fit=crop', '2026-01-20 14:30:00+00', '2026-01-20 14:30:00+00'),
('a0000001-0000-0000-0000-000000000003', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Ho Chi Minh City Culture Trip', '2026-08-05', '2026-08-09', 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?w=400&h=200&fit=crop', '2026-02-01 09:00:00+00', '2026-02-01 09:00:00+00'),
('a0000001-0000-0000-0000-000000000004', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Seoul Weekend', '2026-06-20', '2026-06-22', 'https://images.unsplash.com/photo-1534274988757-a28bf1a57c17?w=400&h=200&fit=crop', '2026-02-10 16:00:00+00', '2026-02-10 16:00:00+00'),
('a0000001-0000-0000-0000-000000000005', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Bangkok Backpacking', '2026-09-01', '2026-09-07', 'https://images.unsplash.com/photo-1508009603885-50cf7c579365?w=400&h=200&fit=crop', '2026-02-15 11:00:00+00', '2026-02-15 11:00:00+00'),
('a0000001-0000-0000-0000-000000000006', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'London City Break', '2026-10-10', '2026-10-14', 'https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=400&h=200&fit=crop', '2026-03-01 08:00:00+00', '2026-03-01 08:00:00+00'),
('a0000001-0000-0000-0000-000000000007', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'New York Photography Tour', '2026-11-01', '2026-11-05', 'https://images.unsplash.com/photo-1496442226666-8d4d0e62e6e9?w=400&h=200&fit=crop', '2026-03-10 13:00:00+00', '2026-03-10 13:00:00+00'),
('a0000001-0000-0000-0000-000000000008', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Barcelona Beach Escape', '2026-07-15', '2026-07-20', 'https://images.unsplash.com/photo-1583422409516-2895a77efded?w=400&h=200&fit=crop', '2026-03-20 10:30:00+00', '2026-03-20 10:30:00+00'),
('a0000001-0000-0000-0000-000000000009', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Singapore Honeymoon', '2026-12-20', '2026-12-25', 'https://images.unsplash.com/photo-1525625293386-3f8f99389edd?w=400&h=200&fit=crop', '2026-04-01 09:00:00+00', '2026-04-01 09:00:00+00'),
('a0000001-0000-0000-0000-000000000010', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Da Nang Family Trip', '2026-08-20', '2026-08-25', 'https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?w=400&h=200&fit=crop', '2026-04-10 15:00:00+00', '2026-04-10 15:00:00+00'),
('a0000001-0000-0000-0000-000000000011', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Rome Solo Explorer', '2026-09-15', '2026-09-19', 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=400&h=200&fit=crop', '2026-04-15 12:00:00+00', '2026-04-15 12:00:00+00'),
('a0000001-0000-0000-0000-000000000012', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Amsterdam Spring Break', '2026-04-01', '2026-04-04', 'https://images.unsplash.com/photo-1534351590666-13e3e96b5017?w=400&h=200&fit=crop', '2026-04-20 08:00:00+00', '2026-04-20 08:00:00+00'),
('a0000001-0000-0000-0000-000000000013', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Dubai Winter Holiday', '2026-12-01', '2026-12-06', NULL, '2026-05-01 10:00:00+00', '2026-05-01 10:00:00+00'),
('a0000001-0000-0000-0000-000000000014', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Osaka Summer Vacation', '2026-07-20', '2026-07-25', NULL, '2026-05-05 14:00:00+00', '2026-05-05 14:00:00+00'),
('a0000001-0000-0000-0000-000000000015', '4af20ff9-f859-40fe-b392-a02c8da8edc8', 'Hanoi Road Trip', '2026-10-01', '2026-10-04', NULL, '2026-05-10 09:00:00+00', '2026-05-10 09:00:00+00');

-- ============================================================
-- ITINERARY DAYS (day_index is 1-based per CHECK constraint)
-- ============================================================

-- Tokyo Adventure (6 days: Jun 10-15)
INSERT INTO trip.itinerary_days (id, trip_id, day_date, day_index) VALUES
('d0000001-0001-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000001', '2026-06-10', 1),
('d0000001-0001-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000001', '2026-06-11', 2),
('d0000001-0001-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000001', '2026-06-12', 3),
('d0000001-0001-0000-0000-000000000004', 'a0000001-0000-0000-0000-000000000001', '2026-06-13', 4),
('d0000001-0001-0000-0000-000000000005', 'a0000001-0000-0000-0000-000000000001', '2026-06-14', 5),
('d0000001-0001-0000-0000-000000000006', 'a0000001-0000-0000-0000-000000000001', '2026-06-15', 6);

-- Paris Food Tour (5 days: Jul 1-5)
INSERT INTO trip.itinerary_days (id, trip_id, day_date, day_index) VALUES
('d0000001-0002-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000002', '2026-07-01', 1),
('d0000001-0002-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000002', '2026-07-02', 2),
('d0000001-0002-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000002', '2026-07-03', 3),
('d0000001-0002-0000-0000-000000000004', 'a0000001-0000-0000-0000-000000000002', '2026-07-04', 4),
('d0000001-0002-0000-0000-000000000005', 'a0000001-0000-0000-0000-000000000002', '2026-07-05', 5);

-- Ho Chi Minh City Culture Trip (5 days: Aug 5-9)
INSERT INTO trip.itinerary_days (id, trip_id, day_date, day_index) VALUES
('d0000001-0003-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000003', '2026-08-05', 1),
('d0000001-0003-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000003', '2026-08-06', 2),
('d0000001-0003-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000003', '2026-08-07', 3),
('d0000001-0003-0000-0000-000000000004', 'a0000001-0000-0000-0000-000000000003', '2026-08-08', 4),
('d0000001-0003-0000-0000-000000000005', 'a0000001-0000-0000-0000-000000000003', '2026-08-09', 5);

-- Seoul Weekend (3 days: Jun 20-22)
INSERT INTO trip.itinerary_days (id, trip_id, day_date, day_index) VALUES
('d0000001-0004-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000004', '2026-06-20', 1),
('d0000001-0004-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000004', '2026-06-21', 2),
('d0000001-0004-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000004', '2026-06-22', 3);

-- Bangkok Backpacking (7 days: Sep 1-7)
INSERT INTO trip.itinerary_days (id, trip_id, day_date, day_index) VALUES
('d0000001-0005-0000-0000-000000000001', 'a0000001-0000-0000-0000-000000000005', '2026-09-01', 1),
('d0000001-0005-0000-0000-000000000002', 'a0000001-0000-0000-0000-000000000005', '2026-09-02', 2),
('d0000001-0005-0000-0000-000000000003', 'a0000001-0000-0000-0000-000000000005', '2026-09-03', 3),
('d0000001-0005-0000-0000-000000000004', 'a0000001-0000-0000-0000-000000000005', '2026-09-04', 4),
('d0000001-0005-0000-0000-000000000005', 'a0000001-0000-0000-0000-000000000005', '2026-09-05', 5),
('d0000001-0005-0000-0000-000000000006', 'a0000001-0000-0000-0000-000000000005', '2026-09-06', 6),
('d0000001-0005-0000-0000-000000000007', 'a0000001-0000-0000-0000-000000000005', '2026-09-07', 7);

-- ============================================================
-- ITINERARY ITEMS (sample items for first 3 trips)
-- ============================================================

-- Tokyo Adventure Day 1 items
INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, time_slot, note, photo_url, created_at, updated_at) VALUES
('10000001-0001-0001-0000-000000000001', 'd0000001-0001-0000-0000-000000000001', 'otm:tokyo_1', 0, '09:00', 'Start early for sunrise views!', 'https://images.unsplash.com/photo-1536098561742-ca998e48cbcc?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),
('10000001-0001-0001-0000-000000000002', 'd0000001-0001-0000-0000-000000000001', 'otm:tokyo_2', 1, '13:00', 'Visit Nakamise shopping street first', 'https://images.unsplash.com/photo-1545569341-9eb8b30979d9?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),
('10000001-0001-0001-0000-000000000003', 'd0000001-0001-0000-0000-000000000001', 'otm:tokyo_3', 2, '17:00', 'Best at sunset', 'https://images.unsplash.com/photo-1542051841857-5f90071e7989?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00');

-- Tokyo Adventure Day 2 items
INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, time_slot, note, photo_url, created_at, updated_at) VALUES
('10000001-0001-0002-0000-000000000001', 'd0000001-0001-0000-0000-000000000002', 'otm:tokyo_4', 0, '08:00', 'Peaceful morning walk', 'https://images.unsplash.com/photo-1478436127897-769e1b3f0f36?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),
('10000001-0001-0002-0000-000000000002', 'd0000001-0001-0000-0000-000000000002', 'otm:tokyo_5', 1, '11:00', 'Fresh sushi for brunch!', 'https://images.unsplash.com/photo-1580442151529-343f2f6e0e27?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00');

-- Tokyo Adventure Day 3 items
INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, time_slot, note, photo_url, created_at, updated_at) VALUES
('10000001-0001-0003-0000-000000000001', 'd0000001-0001-0000-0000-000000000003', 'otm:tokyo_9', 0, '10:00', 'Book tickets online in advance!', 'https://images.unsplash.com/photo-1549888834-3ec93abae044?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),
('10000001-0001-0003-0000-000000000002', 'd0000001-0001-0000-0000-000000000003', 'otm:tokyo_7', 1, '14:00', 'Anime shopping spree', 'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00'),
('10000001-0001-0003-0000-000000000003', 'd0000001-0001-0000-0000-000000000003', 'otm:tokyo_10', 2, '16:30', NULL, 'https://images.unsplash.com/photo-1528360983277-13d401cdc186?w=400&h=300&fit=crop', '2026-01-15 10:00:00+00', '2026-01-15 10:00:00+00');

-- Paris Food Tour Day 1 items
INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, time_slot, note, photo_url, created_at, updated_at) VALUES
('10000001-0002-0001-0000-000000000001', 'd0000001-0002-0000-0000-000000000001', 'otm:paris_1', 0, '09:30', 'Skip the line tickets booked', 'https://images.unsplash.com/photo-1543349689-9a4d426bee8e?w=400&h=300&fit=crop', '2026-01-20 14:30:00+00', '2026-01-20 14:30:00+00'),
('10000001-0002-0001-0000-000000000002', 'd0000001-0002-0000-0000-000000000001', 'otm:paris_2', 1, '14:00', 'Mona Lisa first, then upper floors', 'https://images.unsplash.com/photo-1499856871958-5b9627545d1a?w=400&h=300&fit=crop', '2026-01-20 14:30:00+00', '2026-01-20 14:30:00+00');

-- Paris Food Tour Day 2 items
INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, time_slot, note, photo_url, created_at, updated_at) VALUES
('10000001-0002-0002-0000-000000000001', 'd0000001-0002-0000-0000-000000000002', 'otm:paris_4', 0, '08:00', 'Sunrise from the steps', 'https://images.unsplash.com/photo-1550340499-a6c60fc8287c?w=400&h=300&fit=crop', '2026-01-20 14:30:00+00', '2026-01-20 14:30:00+00'),
('10000001-0002-0002-0000-000000000002', 'd0000001-0002-0000-0000-000000000002', 'otm:paris_7', 1, '10:00', 'Artists quarter exploration', 'https://images.unsplash.com/photo-1594394874822-d5def1a15afd?w=400&h=300&fit=crop', '2026-01-20 14:30:00+00', '2026-01-20 14:30:00+00'),
('10000001-0002-0002-0000-000000000003', 'd0000001-0002-0000-0000-000000000002', 'otm:paris_5', 2, '15:00', 'Impressionist masterpieces', 'https://images.unsplash.com/photo-1541264161754-445bbdd7de52?w=400&h=300&fit=crop', '2026-01-20 14:30:00+00', '2026-01-20 14:30:00+00');

-- Ho Chi Minh City Day 1 items
INSERT INTO trip.itinerary_items (id, itinerary_day_id, destination_ref, position, time_slot, note, photo_url, created_at, updated_at) VALUES
('10000001-0003-0001-0000-000000000001', 'd0000001-0003-0000-0000-000000000001', 'otm:hcmc_1', 0, '09:00', 'Allow 2-3 hours minimum', 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?w=400&h=300&fit=crop', '2026-02-01 09:00:00+00', '2026-02-01 09:00:00+00'),
('10000001-0003-0001-0000-000000000002', 'd0000001-0003-0000-0000-000000000001', 'otm:hcmc_5', 1, '13:00', 'Guided tour recommended', 'https://images.unsplash.com/photo-1555921015-5532091f6026?w=400&h=300&fit=crop', '2026-02-01 09:00:00+00', '2026-02-01 09:00:00+00'),
('10000001-0003-0001-0000-000000000003', 'd0000001-0003-0000-0000-000000000001', 'otm:hcmc_2', 2, '16:00', 'Bargain hard! Start at 50% of asking price', 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?w=400&h=300&fit=crop', '2026-02-01 09:00:00+00', '2026-02-01 09:00:00+00');
