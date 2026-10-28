package helium314.keyboard.latin;

import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;

public class DictionaryLoadingTest {
    @Test public void slowFirstLoadFinishesBeforeSpellchecking() throws Exception {
        final CountDownLatch firstWait = new CountDownLatch(1);
        final CountDownLatch loaded = new CountDownLatch(1);
        final DictionaryFacilitator dictionary = (DictionaryFacilitator) Proxy.newProxyInstance(
                DictionaryFacilitator.class.getClassLoader(), new Class<?>[]{DictionaryFacilitator.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("waitForLoadingMainDictionaries")) {
                        throw new AssertionError("Unexpected dictionary operation: " + method);
                    }
                    firstWait.countDown();
                    return loaded.await((Long) args[0], (TimeUnit) args[1]);
                });
        final Thread loader = new Thread(() -> {
            try {
                firstWait.await();
                Thread.sleep(1250); // Slower than the first one-second wait, within the total limit.
                loaded.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        loader.start();
        try {
            DictionaryFacilitatorLruCache.waitForLoadingMainDictionary(dictionary);
            assertEquals("A timed-out first wait must not be treated as a completed load", 0, loaded.getCount());
        } finally {
            loader.interrupt();
            loader.join();
        }
    }
}
