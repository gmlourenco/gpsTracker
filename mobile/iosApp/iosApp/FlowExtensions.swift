import Foundation
import shared

public func streamCFlow<T: AnyObject>(_ cflow: CFlow<T>) -> AsyncStream<T?> {
    return AsyncStream { continuation in
        let closeable = cflow.watch { value in
            continuation.yield(value)
        }
        continuation.onTermination = { @Sendable _ in
            closeable.close()
        }
    }
}
