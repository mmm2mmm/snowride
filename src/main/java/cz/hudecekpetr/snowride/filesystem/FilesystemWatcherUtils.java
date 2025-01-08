package cz.hudecekpetr.snowride.filesystem;

import cz.hudecekpetr.snowride.tree.highelements.FileSuite;
import cz.hudecekpetr.snowride.tree.highelements.FolderSuite;
import cz.hudecekpetr.snowride.tree.highelements.HighElement;
import cz.hudecekpetr.snowride.ui.MainForm;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FilesystemWatcherUtils {

    private static final ExecutorService pollingProcess = Executors.newSingleThreadExecutor();
    private static final Map<Path, List<WatchEvent<?>>> watchEvents = new ConcurrentHashMap<>();

    /**
     * Workaround for specific implementation of {@link java.nio.file.WatchService}. When on modification of single file multiple watch events are
     * received, not only ENTRY_MODIFY, but also both ENTRY_CREATE and ENTRY_DELETE. Here we are crunching the events together and removing
     * ENTRY_CREATE/ENTRY_DELETE pairs.
     */
    public static void waitToReceiveAllEvents(List<WatchEvent<?>> watchEvent, Path changeDirectory, FilesystemWatcher filesystemWatcher) {
        synchronized (watchEvents) {
            watchEvents.computeIfAbsent(changeDirectory, k -> new java.util.ArrayList<>()).addAll(watchEvent);
        }
        pollingProcess.execute(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Map<Path, List<WatchEvent<?>>> watchEventsToProcess;
            synchronized (watchEvents) {
                watchEvents.forEach((path, events) -> {
                    List<WatchEvent<?>> createEvents = events.stream()
                            .filter(event -> event.kind() == StandardWatchEventKinds.ENTRY_CREATE)
                            .collect(Collectors.toList());
                    List<WatchEvent<?>> deleteEvents = events.stream()
                            .filter(event -> event.kind() == StandardWatchEventKinds.ENTRY_DELETE)
                            .collect(Collectors.toList());
                    events.removeAll(createEvents.stream()
                            .filter(c -> deleteEvents.stream().anyMatch(d -> c.context().equals(d.context())))
                            .collect(Collectors.toList()));
                    events.removeAll(deleteEvents.stream()
                            .filter(d -> createEvents.stream().anyMatch(c -> d.context().equals(c.context())))
                            .collect(Collectors.toList()));
                });
                watchEventsToProcess = new ConcurrentHashMap<>(watchEvents);
                watchEvents.clear();
            }
            watchEventsToProcess.forEach( (p,e)->filesystemWatcher.processWatchEvents(e, p) );
        });
    }

    public static boolean shouldIgnoreFilesystemChangesToFile(File reloadWhat, WatchEvent.Kind<?> kind) {
        // find corresponding 'HighElement' in Snowride
        HighElement highElement = MainForm.INSTANCE.getRootElement().childrenRecursively.stream()
                .filter(element -> (element instanceof FileSuite && ((FileSuite) element).file.equals(reloadWhat)) ||
                        (element instanceof FolderSuite && (((FolderSuite) element).initFile.equals(reloadWhat) || ((FolderSuite) element).directoryPath.equals(reloadWhat))))
                .findFirst()
                .orElse(null);

        // When Snowride does not know about such 'file'
        if (highElement == null) {
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                return false;
            }
            // ignore "DELETE"
            // "MODIFY" does not apply here (we would have found corresponding 'HighElement' otherwise)
            return true;
        }

        // deletion outside Snowride
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return false;
        }

        // creation of 'directory' from Snowride
        if (reloadWhat.isDirectory() && kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return true;
        }

        // compare pristine content
        try {
            return highElement.pristineContents.equals(new String(java.nio.file.Files.readAllBytes(reloadWhat.toPath())));
        } catch (java.io.IOException e) {
            return false;
        }
    }
}