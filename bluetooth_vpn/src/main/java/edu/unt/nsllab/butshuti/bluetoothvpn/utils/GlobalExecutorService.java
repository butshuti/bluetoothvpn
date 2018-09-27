package edu.unt.nsllab.butshuti.bluetoothvpn.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GlobalExecutorService {
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private static final Map<String, Future> pendingTasks = new HashMap<>();
    private static final String THREAD_NAME_SEP = ":: ";

    public static TaskWrapper submit(Runnable task, String label){
        synchronized (pendingTasks){
            if(pendingTasks.containsKey(label) && !(pendingTasks.get(label).isDone() || pendingTasks.get(label).isCancelled())){
                return wrap(pendingTasks.get(label));
            }
        }
        Future f = executorService.submit(getNamedRunnable(task, label));
        pendingTasks.put(label, f);
        return wrap(f);
    }

    public static TaskWrapper schedule(Runnable task, String label, long delay, long interval, TimeUnit timeUnit){
        synchronized (pendingTasks){
            if(pendingTasks.containsKey(label) && !(pendingTasks.get(label).isDone() || pendingTasks.get(label).isCancelled())){
                pendingTasks.get(label).cancel(true);
            }
        }
        Future f;
        if(interval > 0){
            f = scheduledExecutorService.scheduleAtFixedRate(getNamedRunnable(task, label), delay, interval, timeUnit);
        }else{
            f = scheduledExecutorService.schedule(getNamedRunnable(task, label), delay, timeUnit);
        }
        pendingTasks.put(label, f);
        return wrap(f);
    }


    public static void terminate(){
        executorService.shutdownNow();
    }

    private static Runnable getNamedRunnable(Runnable task, String label){
        return new Runnable() {
            @Override
            public void run() {
                String toks[] = Thread.currentThread().getName().split(THREAD_NAME_SEP);
                String name;
                if(toks.length == 2){
                    name = toks[1];
                }else{
                    name = toks[0];
                }
                Thread.currentThread().setName(label + THREAD_NAME_SEP + name);
                task.run();
                Thread.currentThread().setName(name);
            }
        };
    }

    public static TaskWrapper wrap(Future f){
        return new TaskWrapper() {
            @Override
            public boolean isActive() {
                return !(f.isCancelled() || f.isDone());
            }
        };
    }

    public interface TaskWrapper{
        boolean isActive();
    }

}
