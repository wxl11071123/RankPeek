package io.rankpeek.service;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import io.rankpeek.jna.ProcessUtils;
import io.rankpeek.jna.User32;
import io.rankpeek.model.LcuWindowBounds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LcuWindowBoundsService {

    public interface LcuProcessProvider {
        Set<Integer> findLcuProcessIds();
    }

    public interface TopLevelWindowEnumerator {
        List<TopLevelWindow> enumerateVisibleWindows();
    }

    public record TopLevelWindow(long handle, int processId, int x, int y, int width, int height) {
        long area() {
            return Math.max(0, (long) width) * Math.max(0, (long) height);
        }
    }

    private final LcuProcessProvider lcuProcessProvider;
    private final TopLevelWindowEnumerator topLevelWindowEnumerator;

    public LcuWindowBounds findLcuWindowBounds() {
        try {
            Set<Integer> lcuPids = lcuProcessProvider.findLcuProcessIds();
            if (lcuPids.isEmpty()) {
                return LcuWindowBounds.notFound();
            }

            return topLevelWindowEnumerator.enumerateVisibleWindows().stream()
                    .filter(window -> lcuPids.contains(window.processId()))
                    .filter(LcuWindowBoundsService::isUsableClientWindow)
                    .max(Comparator.comparingLong(TopLevelWindow::area))
                    .map(window -> new LcuWindowBounds(
                            true,
                            window.x(),
                            window.y(),
                            window.width(),
                            window.height()
                    ))
                    .orElseGet(LcuWindowBounds::notFound);
        } catch (Exception e) {
            log.warn("Failed to locate LCU window bounds: {}", e.getMessage());
            return LcuWindowBounds.notFound();
        }
    }

    private static boolean isUsableClientWindow(TopLevelWindow window) {
        return window.x() > -30000
                && window.y() > -30000
                && window.width() >= 320
                && window.height() >= 240;
    }
}

@Component
class ProcessUtilsLcuProcessProvider implements LcuWindowBoundsService.LcuProcessProvider {
    @Override
    public Set<Integer> findLcuProcessIds() {
        if (!isWindows()) {
            return Set.of();
        }
        return ProcessUtils.findLcuProcesses().stream().collect(Collectors.toSet());
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}

@Component
class JnaTopLevelWindowEnumerator implements LcuWindowBoundsService.TopLevelWindowEnumerator {
    @Override
    public List<LcuWindowBoundsService.TopLevelWindow> enumerateVisibleWindows() {
        if (!isWindows()) {
            return List.of();
        }

        User32 user32 = User32.INSTANCE;
        List<LcuWindowBoundsService.TopLevelWindow> windows = new ArrayList<>();
        user32.EnumWindows((hWnd, data) -> {
            if (!user32.IsWindowVisible(hWnd) || user32.IsIconic(hWnd)) {
                return true;
            }

            WinDef.RECT rect = new WinDef.RECT();
            if (!user32.GetWindowRect(hWnd, rect)) {
                return true;
            }

            int width = rect.right - rect.left;
            int height = rect.bottom - rect.top;
            if (width <= 0 || height <= 0) {
                return true;
            }

            IntByReference processId = new IntByReference();
            user32.GetWindowThreadProcessId(hWnd, processId);
            windows.add(new LcuWindowBoundsService.TopLevelWindow(
                    Pointer.nativeValue(hWnd.getPointer()),
                    processId.getValue(),
                    rect.left,
                    rect.top,
                    width,
                    height
            ));
            return true;
        }, Pointer.NULL);

        return windows;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
