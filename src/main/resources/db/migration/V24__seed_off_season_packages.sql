-- Demo packages for the project database. Hotel and branch foreign keys are resolved from
-- existing rows so the script does not depend on environment-specific numeric IDs.

INSERT INTO off_season_packages (
    scope, hotel_id, branch_id, code, name, summary, description, inclusions,
    eligible_room_types, terms_and_conditions, image_url, discount_type,
    discount_value, discount_currency, max_discount_amount, minimum_room_subtotal,
    booking_start_date, booking_end_date, stay_start_date, stay_end_date,
    minimum_nights, maximum_nights, minimum_advance_days, max_bookings,
    times_booked, featured, active
)
VALUES
    (
        'GLOBAL', NULL, NULL, 'QUIET-SEASON-12', 'Quiet Season Escape',
        'Save 12% on a relaxed stay during selected quieter travel dates.',
        'A flexible platform-wide package designed to make lower-demand travel periods more attractive.',
        '["Daily breakfast", "Late checkout", "Welcome drink"]'::jsonb,
        '[]'::jsonb, 'Subject to room availability. Package cannot be combined with a promotional code.',
        NULL, 'PERCENTAGE', 12.00, 'USD', 120.00, 250.00,
        CURRENT_DATE - 1, CURRENT_DATE + 90, CURRENT_DATE + 14, CURRENT_DATE + 150,
        2, 10, 2, 250, 0, TRUE, TRUE
    ),
    (
        'GLOBAL', NULL, NULL, 'LONGER-STAY-15', 'Longer Stay Reward',
        'Stay four nights or more and receive 15% off the room price.',
        'Created for customers who can travel outside peak dates and prefer a longer, better-value break.',
        '["Daily breakfast", "Flexible checkout", "Complimentary hot drink"]'::jsonb,
        '[]'::jsonb, 'Minimum four-night stay. Subject to availability and package booking limits.',
        NULL, 'PERCENTAGE', 15.00, 'USD', 250.00, 450.00,
        CURRENT_DATE - 1, CURRENT_DATE + 100, CURRENT_DATE + 21, CURRENT_DATE + 180,
        4, 14, 3, 180, 0, TRUE, TRUE
    )
ON CONFLICT (code) DO NOTHING;

WITH selected_hotel AS (
    SELECT id FROM hotels ORDER BY id LIMIT 1
)
INSERT INTO off_season_packages (
    scope, hotel_id, branch_id, code, name, summary, description, inclusions,
    eligible_room_types, terms_and_conditions, image_url, discount_type,
    discount_value, discount_currency, max_discount_amount, minimum_room_subtotal,
    booking_start_date, booking_end_date, stay_start_date, stay_end_date,
    minimum_nights, maximum_nights, minimum_advance_days, max_bookings,
    times_booked, featured, active
)
SELECT
    'HOTEL', id, NULL, 'HOTEL-WINTER-20', 'Winter Hotel Retreat',
    'Enjoy 20% off across every branch of this hotel during the quieter season.',
    'A hotel-wide package combining meaningful savings with practical benefits for an off-season stay.',
    '["Breakfast for two", "Late checkout", "Room upgrade when available"]'::jsonb,
    '[]'::jsonb, 'Valid at every branch in the selected hotel. Upgrade depends on availability.',
    NULL, 'PERCENTAGE', 20.00, 'USD', 300.00, 300.00,
    CURRENT_DATE - 1, CURRENT_DATE + 75, CURRENT_DATE + 14, CURRENT_DATE + 135,
    3, 7, 3, 100, 0, TRUE, TRUE
FROM selected_hotel
ON CONFLICT (code) DO NOTHING;

WITH selected_hotel AS (
    SELECT id FROM hotels ORDER BY id OFFSET 1 LIMIT 1
)
INSERT INTO off_season_packages (
    scope, hotel_id, branch_id, code, name, summary, description, inclusions,
    eligible_room_types, terms_and_conditions, image_url, discount_type,
    discount_value, discount_currency, max_discount_amount, minimum_room_subtotal,
    booking_start_date, booking_end_date, stay_start_date, stay_end_date,
    minimum_nights, maximum_nights, minimum_advance_days, max_bookings,
    times_booked, featured, active
)
SELECT
    'HOTEL', id, NULL, 'CITY-BREAK-75', 'Quieter City Break',
    'Receive USD 75 off a two-night city stay at any branch of this hotel.',
    'A fixed-value package for customers seeking a short city break outside the busiest dates.',
    '["Daily breakfast", "Early check-in when available", "Local welcome guide"]'::jsonb,
    '[]'::jsonb, 'Minimum room subtotal applies. Early check-in remains subject to availability.',
    NULL, 'FIXED_AMOUNT', 75.00, 'USD', NULL, 350.00,
    CURRENT_DATE - 1, CURRENT_DATE + 80, CURRENT_DATE + 10, CURRENT_DATE + 140,
    2, 6, 1, 120, 0, TRUE, TRUE
FROM selected_hotel
ON CONFLICT (code) DO NOTHING;

WITH selected_hotel AS (
    SELECT id FROM hotels ORDER BY id OFFSET 2 LIMIT 1
)
INSERT INTO off_season_packages (
    scope, hotel_id, branch_id, code, name, summary, description, inclusions,
    eligible_room_types, terms_and_conditions, image_url, discount_type,
    discount_value, discount_currency, max_discount_amount, minimum_room_subtotal,
    booking_start_date, booking_end_date, stay_start_date, stay_end_date,
    minimum_nights, maximum_nights, minimum_advance_days, max_bookings,
    times_booked, featured, active
)
SELECT
    'HOTEL', id, NULL, 'ECO-ESCAPE-18', 'Eco-Conscious Escape',
    'Save 18% on a quieter stay with sustainability-focused inclusions.',
    'This hotel-wide offer encourages lower-demand travel while highlighting environmentally responsible choices.',
    '["Plant-based breakfast option", "Reusable water bottle", "Late checkout"]'::jsonb,
    '[]'::jsonb, 'Sustainability inclusions may vary slightly by branch. Subject to availability.',
    NULL, 'PERCENTAGE', 18.00, 'USD', 220.00, 300.00,
    CURRENT_DATE - 1, CURRENT_DATE + 95, CURRENT_DATE + 20, CURRENT_DATE + 170,
    2, 10, 4, 140, 0, FALSE, TRUE
FROM selected_hotel
ON CONFLICT (code) DO NOTHING;

WITH selected_branch AS (
    SELECT b.id AS branch_id, b.hotel_id
    FROM branches b
    ORDER BY b.id
    LIMIT 1
)
INSERT INTO off_season_packages (
    scope, hotel_id, branch_id, code, name, summary, description, inclusions,
    eligible_room_types, terms_and_conditions, image_url, discount_type,
    discount_value, discount_currency, max_discount_amount, minimum_room_subtotal,
    booking_start_date, booking_end_date, stay_start_date, stay_end_date,
    minimum_nights, maximum_nights, minimum_advance_days, max_bookings,
    times_booked, featured, active
)
SELECT
    'BRANCH', hotel_id, branch_id, 'BRANCH-BREAKFAST-10', 'Breakfast Branch Getaway',
    'Save 10% at this branch and enjoy breakfast throughout your stay.',
    'A branch-specific package offering a simple room saving and a useful daily inclusion.',
    '["Daily breakfast", "Welcome refreshment", "Late checkout"]'::jsonb,
    '[]'::jsonb, 'Available only at the selected branch and subject to package capacity.',
    NULL, 'PERCENTAGE', 10.00, 'USD', 100.00, 200.00,
    CURRENT_DATE - 1, CURRENT_DATE + 70, CURRENT_DATE + 7, CURRENT_DATE + 120,
    2, 8, 1, 75, 0, FALSE, TRUE
FROM selected_branch
ON CONFLICT (code) DO NOTHING;

WITH selected_branch AS (
    SELECT b.id AS branch_id, b.hotel_id
    FROM branches b
    ORDER BY b.id
    OFFSET 1
    LIMIT 1
)
INSERT INTO off_season_packages (
    scope, hotel_id, branch_id, code, name, summary, description, inclusions,
    eligible_room_types, terms_and_conditions, image_url, discount_type,
    discount_value, discount_currency, max_discount_amount, minimum_room_subtotal,
    booking_start_date, booking_end_date, stay_start_date, stay_end_date,
    minimum_nights, maximum_nights, minimum_advance_days, max_bookings,
    times_booked, featured, active
)
SELECT
    'BRANCH', hotel_id, branch_id, 'BRANCH-SPA-120', 'Spa Season Retreat',
    'Receive USD 120 off a premium off-season stay at this branch.',
    'A limited branch package designed around a higher-value relaxing stay during quieter dates.',
    '["Spa access", "Breakfast for two", "Extended checkout"]'::jsonb,
    '[]'::jsonb, 'Minimum room subtotal applies. Spa booking times must be arranged separately.',
    NULL, 'FIXED_AMOUNT', 120.00, 'USD', NULL, 600.00,
    CURRENT_DATE - 1, CURRENT_DATE + 85, CURRENT_DATE + 21, CURRENT_DATE + 150,
    3, 9, 5, 60, 0, TRUE, TRUE
FROM selected_branch
ON CONFLICT (code) DO NOTHING;
