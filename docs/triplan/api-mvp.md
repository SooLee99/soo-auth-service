# Triplan API MVP

## APIs

1. POST /recommendations/destinations
2. POST /itineraries/calculate
3. POST /itineraries

## Recommendation

- TourAPI: attraction, festival, accommodation, image
- Kakao Local: cafe CE7, restaurant FD6
- Normalize source, externalId, placeType
- Remove duplicates by name and near location
- Score by styleMatch, regionFit, sourcePriority, popularity, seasonality

## Itinerary Calculation

- Keep source, externalId, placeType
- Route provider for distance and ETA
- Weather provider by day
- Return calculationVersion

## Itinerary Save

- Validate day continuity
- Validate stop order
- Store source, externalId, placeType
- Use optimistic locking later
