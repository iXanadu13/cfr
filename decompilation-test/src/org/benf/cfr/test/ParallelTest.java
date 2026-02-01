package org.benf.cfr.test;

import org.benf.cfr.reader.api.CfrDriver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ParallelTest {
    CountDownLatch latch = new CountDownLatch(8);
    //    Map<String, Boolean> options = new HashMap<>();
    @Test
    public void parallelTest() throws Exception {
//        Ruffee.initWorkspace(List.of("E:\\tmp\\stackoverflow-sample.jar"));
//        CFRDecompiler.setOptions(options);
        for (int i = 0; i < 4; ++i) {
            spawnThread("org/inksnow/qreplace/N");
            spawnThread("org/inksnow/qreplace/B");
        }
        latch.await();
    }
    private void spawnThread(String className) {
//        byte[] data = Ruffee.getCurrentWorkspace().findJvmClass(className + ".class");
        new Thread(() -> {
            CfrDriver driver = new CfrDriver.Builder().build();
            driver.analyse(List.of("E:\\tmp\\QReplace-1.0.jar"));
//            var decompile = CFRDecompiler.decompile(className, data);
//            var status = decompile.getStatus();
//            Assertions.assertEquals(status, 0);
            latch.countDown();
        }).start();
    }
}
