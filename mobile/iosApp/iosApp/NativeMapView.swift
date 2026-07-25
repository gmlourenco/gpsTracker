import SwiftUI
import MapKit
import shared

class CustomAnnotation: MKPointAnnotation {
    var markerLetter: String = ""
    var uiColor: UIColor = .blue
}

struct NativeMapView: UIViewRepresentable {
    @Binding var region: MKCoordinateRegion
    var markers: [SwiftFamilyMarker]
    var mapType: MKMapType
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = true
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        if uiView.mapType != mapType {
            uiView.mapType = mapType
        }
        
        // Update region without interrupting user panning if it's the same center
        let distance = abs(uiView.region.center.latitude - region.center.latitude) + abs(uiView.region.center.longitude - region.center.longitude)
        if distance > 0.0001 {
            uiView.setRegion(region, animated: true)
        }
        
        // Update annotations efficiently
        let currentAnnotations = uiView.annotations.compactMap { $0 as? CustomAnnotation }
        
        // Find markers to add
        let newMarkers = markers.filter { marker in
            !currentAnnotations.contains(where: { $0.title == marker.label })
        }
        
        // Find annotations to remove
        let removedAnnotations = currentAnnotations.filter { ann in
            !markers.contains(where: { $0.label == ann.title })
        }
        
        if !removedAnnotations.isEmpty {
            uiView.removeAnnotations(removedAnnotations)
        }
        
        if !newMarkers.isEmpty {
            let newAnnotations = newMarkers.map { marker -> CustomAnnotation in
                let annotation = CustomAnnotation()
                annotation.coordinate = CLLocationCoordinate2D(latitude: marker.lat, longitude: marker.lng)
                annotation.title = marker.id
                annotation.markerLetter = marker.markerLetter
                annotation.uiColor = marker.uiColor
                return annotation
            }
            uiView.addAnnotations(newAnnotations)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: NativeMapView
        
        init(_ parent: NativeMapView) {
            self.parent = parent
        }
        
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            parent.region = mapView.region
        }
        
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            
            guard let customAnnotation = annotation as? CustomAnnotation else { return nil }
            
            let identifier = "CustomAnnotation"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
            
            if view == nil {
                view = MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = true
            } else {
                view?.annotation = annotation
            }
            
            // Create a custom view (Circle with text)
            let circleView = UIView(frame: CGRect(x: 0, y: 0, width: 36, height: 36))
            circleView.backgroundColor = customAnnotation.uiColor
            circleView.layer.cornerRadius = 18
            circleView.layer.borderWidth = 2
            circleView.layer.borderColor = UIColor.white.cgColor
            circleView.layer.shadowColor = UIColor.black.cgColor
            circleView.layer.shadowOpacity = 0.3
            circleView.layer.shadowOffset = CGSize(width: 0, height: 2)
            circleView.layer.shadowRadius = 2
            
            let label = UILabel(frame: circleView.bounds)
            label.text = customAnnotation.markerLetter
            label.textColor = .white
            label.textAlignment = .center
            label.font = .systemFont(ofSize: 16, weight: .bold)
            circleView.addSubview(label)
            
            // Generate an image from the view to set as the image of the annotation view
            let renderer = UIGraphicsImageRenderer(size: circleView.bounds.size)
            let image = renderer.image { ctx in
                circleView.layer.render(in: ctx.cgContext)
            }
            
            view?.image = image
            return view
        }
    }
}
