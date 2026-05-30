/**
 * Utility functions for generating mapping and navigation URLs.
 */

/**
 * Returns a URL to preview routing to a destination on Google Maps.
 */
export function getGoogleMapsDirectionsUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
}

/**
 * Returns a URL to view a single point location on Google Maps.
 */
export function getGoogleMapsSearchUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;
}

/**
 * Returns a URL to navigate to a destination on Waze.
 */
export function getWazeDirectionsUrl(lat: number, lng: number): string {
  return `https://waze.com/ul?ll=${lat},${lng}&navigate=yes`;
}
