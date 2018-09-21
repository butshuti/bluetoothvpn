package edu.unt.nsllab.butshuti.bluetoothvpn.utils;

import android.util.Log;

public class Logger {
	private static String tag = Logger.class.getPackage().getName();
	private static LogTracer logTracer = null;
	static{
		if(logTracer == null) {
			synchronized (Logger.class) {
				if (logTracer == null) {
					logTracer = new LogTracer();
				}
			}
		}
	}

	public static void setTag(String newTag) {
		if (newTag != null) {
			tag = newTag;
		}
	}

	public static void logI(String s) {
		Log.i(tag, s);
	}

	public static void logE(String s) {
		Log.e(tag, logTracer.traceWithCaller(s));
	}

	public static void logW(String s) {
		Log.w(tag, s);
	}

	public static void logD(String s) {
		Log.d(tag, logTracer.traceWithCaller(s));
	}

	private static class LogTracer {
		private static final String traceWithCaller(String msg) {
			String caller = "";
			//Called as CallingClass -> Logger -> LogTracer. We are interested in CallingClass.
			StackTraceElement ste[] = new Throwable().getStackTrace();
			if (ste != null && ste.length >= 4) {
				caller = "[" + ste[3].getLineNumber() + "]" + ste[3].getClassName() + "#" + ste[3].getMethodName();
			}
			return " > " + caller + "() : " + msg;
		}
	}
}
