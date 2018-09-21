package edu.unt.nsllab.butshuti.bluetoothvpn.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * Created by butshuti on 9/12/18.
 */

public class CMDUtils {

    public static class CMDResult{
        boolean success;
        String result;

        public CMDResult(boolean success, String result){
            this.success = success;
            this.result = result;
        }

        public boolean failed(){
            return !success;
        }

        public String getResult(){
            return result;
        }
    }

    public static CMDResult exec(String cmd){
        Runtime runtime = Runtime.getRuntime();
        String ret = "";
        boolean success = false;
        try
        {
            Process  process = runtime.exec(cmd);
            if(process.waitFor() == 0){
                success = true;
                Scanner sc = new Scanner(process.getInputStream()).useDelimiter("\\A");
                if(sc.hasNext()){
                    ret = sc.next();
                }
            }else{
                Logger.logE("EXEC: error.");
            }
        } catch (InterruptedException e) {
            Logger.logE(e.getMessage());
        } catch (IOException e) {
            Logger.logE(e.getMessage());
        }
        return new CMDResult(success, ret);
    }
}
