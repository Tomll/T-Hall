package com.transage.hall.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;

public class HallStateUtils {

    private static HallStateUtils mHallStateUtils = new HallStateUtils();
    private static HashMap<String,Object> callInfos =new HashMap<String, Object>();
    public static HallStateUtils getInstance() {
        return mHallStateUtils;
    }

    private static final String HALL_STATE_CHECKPATH = "/sys/class/switch/hall/state";
    private static final int HALL_COVERED_CODE = 48;

    /**
    * Get the state for the hall.
    * 
    * @return boolean true:covered;false:not covered.
    */
    public static boolean isHallCovered() {
        FileInputStream fips = null;
        int codeValue = 0;
        try {
            fips = new FileInputStream(HALL_STATE_CHECKPATH);
            codeValue = fips.read();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fips != null) {
                try {
                    fips.close();
                    fips = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        android.util.Log.d("shixu",
                "KeyguardViewMediator----isHallCovered()#codeValue = "
                    + codeValue);
        if (HALL_COVERED_CODE == codeValue) {
            return true;
        } else {
            return false;
        }
    }

    public void setCallInfos(String name,String number,boolean nameIsNumber){
       callInfos.put("name",name);
       callInfos.put("number",number);
       callInfos.put("nameIsNumber",nameIsNumber);
    }

    public static HashMap<String,Object> getCallInfos(){
       return callInfos;
    }
}
