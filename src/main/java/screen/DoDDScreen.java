/*
 * This file is part of the Adafruit OLED Bonnet Toolkit: a Java toolkit for the Adafruit 128x64 OLED bonnet,
 * with support for the screen, D-pad/buttons, UI layout, and task scheduling.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/lukehutch/Adafruit-OLED-Bonnet-Toolkit
 * 
 * This code is not associated with or endorsed by Adafruit. Adafruit is a trademark of Limor "Ladyada" Fried. 
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package screen;

import java.util.ArrayList;
import java.util.Queue;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import aobtk.font.Font;
import aobtk.hw.HWButton;
import aobtk.i18n.Str;
import aobtk.oled.OLEDDriver;
import aobtk.ui.element.ProgressBar;
import aobtk.ui.element.TableLayout;
import aobtk.ui.element.TextElement;
import aobtk.ui.element.VLayout;
import aobtk.ui.element.VLayout.VAlign;
import aobtk.ui.screen.Screen;
import exec.Exec;
import i18n.Msg;
import main.Main;
import util.DiskMonitor;
import util.DriveInfo;

public class DoDDScreen extends Screen {
    private final DriveInfo selectedDrive;
    private volatile List<DriveInfo> destDrives;
    private final List<ProgressBar> progressBars;

    private List<Future<Integer>> ddFutures;
    private final AtomicBoolean canceled = new AtomicBoolean(false);

    public DoDDScreen(Screen parentScreen, DriveInfo selectedDrive, int numFiles, List<DriveInfo> otherDrives) {
        super(parentScreen);

        this.selectedDrive = selectedDrive;
        this.destDrives = otherDrives;

        VLayout layout = new VLayout();

        layout.add(new TextElement(Main.UI_FONT.newStyle(), new Str(Msg.COPYING, selectedDrive.port)),
                VAlign.CENTER);
        layout.addSpace(1, VAlign.CENTER);
        layout.add(new TextElement(Main.UI_FONT.newStyle(), new Str(Msg.DISK_SIZE, selectedDrive.realDiskSizeHuman)), VAlign.CENTER);
        layout.addSpace(1, VAlign.CENTER);

        // Create table of progress bars
        progressBars = new ArrayList<>(otherDrives.size());
        TableLayout driveTable = new TableLayout(2, 0);
        for (DriveInfo otherDrive : otherDrives) {
            ProgressBar progressBar = new ProgressBar(OLEDDriver.DISPLAY_WIDTH * 8 / 10, 10);
            progressBars.add(progressBar);
            driveTable.add(new TextElement(Font.PiOLED_5x8().newStyle(), "->#" + otherDrive.port), progressBar);
        }

        layout.add(driveTable, VAlign.CENTER);

        setUI(layout);
    }
    private Queue<String> ddStderrLines = new ConcurrentLinkedDeque<>();

    @Override
    public void open() {
        ddFutures = new ArrayList<>(destDrives.size());
        AtomicBoolean succeeded = new AtomicBoolean(true);
        for (int i = 0; i < destDrives.size(); i++) {
            DriveInfo destDrive = destDrives.get(i);
            ProgressBar progressBar = progressBars.get(i);

            // Spawn a separate dd task per drive. This parallelizes copying to multiple drives,
            // which could load the USB bus pretty heavily, but it has the advantage of reusing the
            // cache, assuming the destination drives have approximately the same write speed.
            Future<Integer> ddTask = Exec.execConsumingLines(null, stderrLine -> {
                ddStderrLines.add(stderrLine);
                if (!stderrLine.isEmpty() && !stderrLine.contains("records in")) {
                    // Show progress percentage
                    int spaceIdx = stderrLine.indexOf(' ');
                    if (spaceIdx < 0) {
                        spaceIdx = stderrLine.length();
                    }
                    long bytesProcessed = Long.parseLong(stderrLine.substring(0, spaceIdx));
                    int percent = (int) ((bytesProcessed * 100.0f) / selectedDrive.realDiskSize + 0.5f);
                    progressBar.setProgress(percent, 100);
                    repaint();
                }
            }, //
                    "dd", "if=" + selectedDrive.rawDriveDevice, "of=" + destDrive.rawDriveDevice, "bs=64K",
                    "status=progress", "oflag=direct");

            // Set each progress meter to 100% at the end of the copy, since "dd --progress2" will show 0%
            // on termination if all files have already been copied, rather than 100%.
            // Need to do this in a separate task, because the stdout handler above doesn't know when it has
            // read the last line of stdout.
            ddFutures.add(Exec.executor.submit(() -> {
                int exitCode;
                try {
                    exitCode = ddTask.get();
                } catch (InterruptedException | CancellationException e) {
                    // Pass cancelation back to ddTask
                    ddTask.cancel(true);
                    throw e;
                }
                // Use 105 as the denominator, since there's still some work to do at the end of copy to
                // sync and unmount
                progressBar.setProgress(100, 105);
                return exitCode;
            }));
        }

        // Wait for all dd tasks to terminate
        Exec.executor.submit(() -> {
            for (int driveIdx = 0; driveIdx < ddFutures.size(); driveIdx++) {
                Future<Integer> task = ddFutures.get(driveIdx);
                DriveInfo destDrive = destDrives.get(driveIdx);
                try {
                    // Wait for copy operation to complete
                    Integer resultCode = task.get();
                    if (resultCode != 0) {
                        System.out.println(
                                "Copy to " + destDrive.partitionDevice + " failed with result code " + resultCode);
                        succeeded.set(false);
                    }
                    System.out.println("Copy to " + destDrive.partitionDevice + " succeeded");

                } catch (InterruptedException | CancellationException e) {
                    System.out.println("Copy to " + destDrive.partitionDevice + " canceled");
                    canceled.set(true);

                } catch (ExecutionException e) {
                    System.out.println("Copy to " + destDrive.partitionDevice + " failed: " + e);
                    succeeded.set(false);
                }
            }

            // Run sync, shut down task executors, and mark drives as changed
            stopCopying();

            if (!canceled.get() && succeeded.get()) {
                // Set all progress bars to 100% at end
                for (int i = 0; i < destDrives.size(); i++) {
                    ProgressBar progressBar = progressBars.get(i);
                    if (progressBar != null) {
                        progressBar.setProgress(100, 100);
                    }
                }
                repaint();
                // Sleep for a moment to display 100% progress
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
            }

            // Set completion or error message
            setUI(new VLayout(new TextElement(Main.UI_FONT.newStyle(),
                    canceled.get() ? Msg.CANCELED : succeeded.get() ? Msg.COMPLETED : Msg.ERROR)));

            // Go to parent screen after a timeout
            waitThenGoToParentScreen(succeeded.get() ? 2000 : 3000);
        });
    }

    private void stopCopying() {
        if (canceled.get()) {
            for (int driveIdx = 0; driveIdx < ddFutures.size(); driveIdx++) {
                Future<Integer> task = ddFutures.get(driveIdx);
                DriveInfo destDrive = destDrives.get(driveIdx);
                System.out.println("Canceling task for copy to " + destDrive.partitionDevice);
                task.cancel(true);
            }
        }

        DiskMonitor.sync();

        for (int i = 0; i < destDrives.size(); i++) {
            DriveInfo driveInfo = destDrives.get(i);

            // Update drive size info
            driveInfo.updateDriveSizeAsync();

            driveInfo.clearListing();

            driveInfo.updateRealDiskSize();

            driveInfo.updateRealDiskSizeHuman();

            // Unmount so the drives can be pulled out without setting the dirty bit
            driveInfo.unmount();
        }
    }

    @Override
    public void close() {
        stopCopying();
    }

    @Override
    public boolean acceptsButtonA() {
        return true;
    }

    @Override
    public void buttonDown(HWButton button) {
        if (button == HWButton.A) {
            // Tell the other thread that errors are due to cancellation
            canceled.set(true);
            
            setUI(new VLayout(new TextElement(Main.UI_FONT.newStyle(), Msg.CANCELING)));

            // Cancel file listing operation if it hasn't finished yet
            stopCopying();
        }
    }

}