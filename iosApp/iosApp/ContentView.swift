import UIKit
import SwiftUI
import UniformTypeIdentifiers
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeCoordinator() -> Documents { Documents() }
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(documents: context.coordinator)
        context.coordinator.presenter = controller
        return controller
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

final class Documents: NSObject, IosDocuments, UIDocumentPickerDelegate {
    weak var presenter: UIViewController?
    private var saveCompletion: ((KotlinBoolean, String?) -> KotlinUnit)?
    private var loadCompletion: ((String?, String?) -> KotlinUnit)?
    private var temporary: URL?

    func save(name: String, content: String, completion: @escaping (KotlinBoolean, String?) -> KotlinUnit) {
        saveCompletion = completion
        do {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let url = directory.appendingPathComponent(name)
            temporary = directory
            try Data(content.utf8).write(to: url, options: .atomic)
            let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
            present(picker)
        } catch { finish(error: "The export file could not be created.") }
    }

    func load(completion: @escaping (String?, String?) -> KotlinUnit) {
        loadCompletion = completion
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .data], asCopy: true)
        picker.allowsMultipleSelection = false
        present(picker)
    }

    private func present(_ picker: UIDocumentPickerViewController) {
        picker.delegate = self
        guard let presenter else { finish(error: "Document picker is unavailable."); return }
        var top = presenter
        while let next = top.presentedViewController { top = next }
        top.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if saveCompletion != nil { finish(saved: true); return }
        guard let url = urls.first else { finish(); return }
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        do {
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size <= 20 * 1024 * 1024 else { finish(error: "Backup exceeds 20 MB."); return }
            let content = try String(contentsOf: url, encoding: .utf8)
            finish(content: content)
        } catch { finish(error: "The backup could not be read.") }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { finish() }

    private func finish(saved: Bool = false, content: String? = nil, error: String? = nil) {
        _ = saveCompletion?(KotlinBoolean(value: saved), error)
        _ = loadCompletion?(content, error)
        saveCompletion = nil
        loadCompletion = nil
        if let temporary { try? FileManager.default.removeItem(at: temporary) }
        temporary = nil
    }
}

struct ContentView: View {
    var body: some View { ComposeView().ignoresSafeArea() }
}
