#include "catalog.h"
#include "catalog/catalog_driver.h"
#include "catalog/catalog_ride.h"
#include "catalog/catalog_user.h"
#include "catalog/catalog_city.h"

#include "benchmark.h"
#include "price_util.h"

/**
 * Struct that represents a catalog.
 */
struct Catalog {
    CatalogUser *catalog_user;
    CatalogDriver *catalog_driver;
    CatalogRide *catalog_ride;

    CatalogCity *catalog_city;
};

Catalog *create_catalog(void) {
    Catalog *catalog = malloc(sizeof(struct Catalog));

    catalog->catalog_user = create_catalog_user();
    catalog->catalog_driver = create_catalog_driver();
    catalog->catalog_ride = create_catalog_ride();

    catalog->catalog_city = create_catalog_city();

    return catalog;
}

void free_catalog(Catalog *catalog) {
    free_catalog_user(catalog->catalog_user);
    free_catalog_driver(catalog->catalog_driver);
    free_catalog_ride(catalog->catalog_ride);

    free_catalog_city(catalog->catalog_city);

    free(catalog);
}

char *catalog_get_city_name(Catalog *catalog, int city_id) {
    return catalog_city_get_city_name(catalog->catalog_city, city_id);
}

int catalog_get_city_id(Catalog *catalog, char *city) {
    return catalog_city_get_city_id(catalog->catalog_city, city);
}

/**
 * Internal function that parses a line and registers the parsed user.
 */
static inline void internal_parse_and_register_user(Catalog *catalog, TokenIterator *line_iterator) {
    User *user = parse_line_user(line_iterator);
    if (user == NULL) return;

    catalog_user_register_user(catalog->catalog_user, user);
}

void parse_and_register_user(void *catalog, TokenIterator *line_iterator) {
    internal_parse_and_register_user(catalog, line_iterator);
}

/**
 * Internal function that parses a line and registers the parsed driver.
 */
static inline void internal_parse_and_register_driver(Catalog *catalog, TokenIterator *line_iterator) {
    char *city;
    Driver *driver = parse_line_driver_detailed(line_iterator, &city);
    if (driver == NULL) return;

    int city_id = catalog_city_get_or_register_city_id(catalog->catalog_city, city);
    driver_set_city_id(driver, city_id);

    catalog_driver_register_driver(catalog->catalog_driver, driver);
}

void parse_and_register_driver(void *catalog, TokenIterator *line_iterator) {
    internal_parse_and_register_driver(catalog, line_iterator);
}

/**
 * Internal function that parses a line and registers the parsed ride.
 */
static inline void internal_parse_and_register_ride(Catalog *catalog, TokenIterator *line_iterator) {
    char *city;
    char *user_username;

    Ride *ride = parse_line_ride_detailed(line_iterator, &city, &user_username);
    if (ride == NULL) return;
    int city_id = catalog_city_get_or_register_city_id(catalog->catalog_city, city);
    ride_set_city_id(ride, city_id);

    int driver_id = ride_get_driver_id(ride);
    Driver *driver = catalog_get_driver(catalog, driver_id);
    double price = compute_price(ride_get_distance(ride), driver_get_car_class(driver));
    ride_set_price(ride, price);

    double total_price = ride_get_tip(ride) + price;

    driver_increment_number_of_rides(driver);
    int driver_score = ride_get_score_driver(ride);
    driver_add_score(driver, driver_score);
    driver_add_earned(driver, total_price);
    driver_register_ride_date(driver, ride_get_date(ride));

    User *user = catalog_get_user_by_username(catalog, user_username);
    user_increment_number_of_rides(user);
    user_add_score(user, ride_get_score_user(ride));
    user_add_spent(user, total_price);
    user_add_total_distance(user, ride_get_distance(ride));
    user_register_ride_date(user, ride_get_date(ride));

    // The user id has already been generated by the user's catalog
    int user_id = user_get_id(user);
    ride_set_user_id(ride, user_id);

    catalog_ride_register_ride(catalog->catalog_ride, ride);

    AccountStatus driver_account_status = driver_get_account_status(driver);
    AccountStatus user_account_status = user_get_account_status(user);

    if (driver_account_status == ACTIVE) { // We only need to index for query 7 if the driver is active
        catalog_driver_register_driver_ride(catalog->catalog_driver, driver, driver_score, city_id);
    }

    if (driver_account_status == ACTIVE && user_account_status == ACTIVE) { // We only need to index for query 8 if both driver and user is active
        Gender user_gender = user_get_gender(user);
        Gender driver_gender = driver_get_gender(driver);
        if (user_gender == driver_gender) {
            Date user_account_creation_date = user_get_account_creation_date(user);
            Date driver_account_creation_date = driver_get_account_creation_date(driver);

            ride_set_user_account_creation_date(ride, user_account_creation_date);
            ride_set_driver_account_creation_date(ride, driver_account_creation_date);

            catalog_ride_register_ride_same_gender(catalog->catalog_ride, user_gender, ride);
        }
    }
}

void parse_and_register_ride(void *catalog, TokenIterator *line_iterator) {
    internal_parse_and_register_ride(catalog, line_iterator);
}

User *catalog_get_user_by_user_id(Catalog *catalog, int user_id) {
    return catalog_user_get_user_by_user_id(catalog->catalog_user, user_id);
}

User *catalog_get_user_by_username(Catalog *catalog, char *username) {
    return catalog_user_get_user_by_username(catalog->catalog_user, username);
}

Driver *catalog_get_driver(Catalog *catalog, int id) {
    return catalog_driver_get_driver(catalog->catalog_driver, id);
}

int query_2_catalog_get_top_drivers_with_best_score(Catalog *catalog, int n, GPtrArray *result) {
    return catalog_driver_get_top_n_drivers_with_best_score(catalog->catalog_driver, n, result);
}

int query_3_catalog_get_top_users_with_longest_total_distance(Catalog *catalog, int n, GPtrArray *result) {
    return catalog_user_get_top_n_users(catalog->catalog_user, n, result);
}

double query_4_catalog_get_average_price_in_city(Catalog *catalog, int city_id) {
    return catalog_ride_get_average_price_in_city(catalog->catalog_ride, city_id);
}

double query_5_catalog_get_average_price_in_date_range(Catalog *catalog, Date start_date, Date end_date) {
    return catalog_ride_get_average_distance_in_date_range(catalog->catalog_ride, start_date, end_date);
}

double query_6_catalog_get_average_distance_in_city_by_date(Catalog *catalog, Date start_date, Date end_date, int city_id) {
    return catalog_ride_get_average_distance_in_city_and_date_range(catalog->catalog_ride, start_date, end_date, city_id);
}

int query_7_catalog_get_top_n_drivers_in_city(Catalog *catalog, int n, int city_id, GPtrArray *result) {
    return catalog_driver_get_top_n_drivers_with_best_score_by_city(catalog->catalog_driver, city_id, n, result);
}

int query_8_catalog_get_rides_with_user_and_driver_with_same_gender_above_acc_age(Catalog *catalog, GPtrArray *result, Gender gender, int min_account_age) {
    return catalog_ride_get_rides_with_user_and_driver_with_same_age_above_acc_age(catalog->catalog_ride, result, gender, min_account_age);
}

void query_9_catalog_get_passengers_that_gave_tip_in_date_range(Catalog *catalog, GPtrArray *result, Date start_date, Date end_date) {
    catalog_ride_get_passengers_that_gave_tip_in_date_range(catalog->catalog_ride, start_date, end_date, result);
}

void catalog_force_eager_indexing(Catalog *catalog) {
    BENCHMARK_START(load_timer);

    catalog_driver_force_eager_indexing(catalog->catalog_driver);
    catalog_user_force_eager_indexing(catalog->catalog_user);
    catalog_ride_force_eager_indexing(catalog->catalog_ride);

    BENCHMARK_END(load_timer, "Final indexing time:    %f seconds\n");
}
