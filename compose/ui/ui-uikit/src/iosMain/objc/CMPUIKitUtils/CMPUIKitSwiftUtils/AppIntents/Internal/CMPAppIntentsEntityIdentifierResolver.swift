/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#if compiler(>=6.4)
import AppIntents
import Foundation

@available(iOS 18.4, *)
struct CMPAppIntentsEntityIdentifierResolver {
    let resolve: (String, String) -> EntityIdentifier?
}

@available(iOS 18.4, *)
extension CMPAppIntentsEntityIdentifierResolver {
    static func live(bundle: Bundle) -> Self {
        let metadata: AppIntentsMetadata?
        if let metadataURL = bundle.appintentsMetadataURL {
            do {
                metadata = try JSONDecoder().decode(AppIntentsMetadata.self, from: Data(contentsOf: metadataURL))
            } catch {
                NSLog("[CMPAppIntents] metadata decode failed: %@", error.localizedDescription)
                metadata = nil
            }
        } else {
            NSLog("[CMPAppIntents] metadata unavailable: Metadata.appintents/extract.actionsdata")
            metadata = nil
        }

        return Self { typeName, rawIdentifier in
            guard let entityTypeMetadata = metadata?.entities[typeName] else {
                NSLog("[CMPAppIntents] metadata missing type=%@", typeName)
                return nil
            }

            guard let runtimeType = _typeByName(entityTypeMetadata.mangledTypeName) else {
                NSLog("[CMPAppIntents] type lookup failed type=%@", typeName)
                return nil
            }

            guard let entityType = runtimeType as? any AppEntity.Type else {
                NSLog("[CMPAppIntents] resolved non-AppEntity type=%@", typeName)
                return nil
            }

            guard let identifier = EntityIdentifier(for: entityType, rawIdentifier: rawIdentifier) else {
                NSLog("[CMPAppIntents] identifier rejected type=%@", typeName)
                return nil
            }
            return identifier
        }
    }
}

private extension Bundle {
    var appintentsMetadataURL: URL? {
        url(forResource: "extract", withExtension: "actionsdata", subdirectory: "Metadata.appintents")
    }
}

private extension EntityIdentifier {
    init?<Entity: AppEntity>(for entityType: Entity.Type, rawIdentifier: String) {
        guard let identifier = Entity.ID.entityIdentifier(for: rawIdentifier) else { return nil }
        self.init(for: entityType, identifier: identifier)
    }
}

private struct AppIntentsMetadata: Decodable {
    let entities: [String: EntityMetadata]
}

private struct EntityMetadata: Decodable {
    let mangledTypeName: String
}
#endif
