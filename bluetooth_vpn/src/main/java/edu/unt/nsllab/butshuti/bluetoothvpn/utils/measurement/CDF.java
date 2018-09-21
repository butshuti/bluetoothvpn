package edu.unt.nsllab.butshuti.bluetoothvpn.utils.measurement;

import android.support.annotation.NonNull;
import android.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CDF {
        private float min = 0, max = 0, mean = 0;
        private ComparablePair probDist[];

        public CDF(float values[], float resolution){
            if(values != null && values.length > 0){
                min = Float.MAX_VALUE;
                max = Float.MIN_VALUE;
                Map<Float, Integer> counts = new HashMap<>();
                float sum = 0;
                for(float val : values){
                    if(val < min){
                        min = val;
                    }
                    if(val > max){
                        max = val;
                    }
                    Float vVal = resolution * Math.round(val / resolution);
                    sum += vVal;
                    if(counts.containsKey(vVal)){
                        counts.put(vVal, counts.get(vVal)+1);
                    }else{
                        counts.put(vVal, 1);
                    }
                }
                mean = sum / values.length;
                probDist = new ComparablePair[counts.keySet().size()];
                int num = 0;
                int maxCount = 0;
                for(Map.Entry<Float, Integer> entry : counts.entrySet()){
                    Integer count = entry.getValue();
                    probDist[num++] = new ComparablePair(entry.getKey(), count.floatValue()/values.length);
                    if(count > maxCount){
                        maxCount = count;
                    }
                }
            }  else{
                min = max = mean = 0;
                probDist = new ComparablePair[]{new ComparablePair(max, 0)};
            }
        }

        public float getMin(){
            return min;
        }

        public float getMax(){
            return max;
        }

        public float getMean(){
            return mean;
        }

        public Pair<Float, Float>[] getCumDist(){
            return ComparablePair.sortPairs(probDist);
        }

    private static class ComparablePair implements Comparable<ComparablePair> {
        float first, second;

        ComparablePair(float first, float second){
            this.first = first;
            this.second = second;
        }

        static Pair<Float, Float>[] sortPairs(ComparablePair comparablePairs[]){
            Arrays.sort(comparablePairs);
            float sum = 0;
            for(ComparablePair pair: comparablePairs){
                sum += pair.second;
                pair.second = sum;
            }
            Pair<Float, Float>[] ret = (Pair<Float, Float>[])new Pair[comparablePairs.length];
            for(int i=0; i<ret.length; i++){
                ret[i] = new Pair<>(comparablePairs[i].first, comparablePairs[i].second);
            }
            return ret;
        }

        @Override
        public int compareTo(@NonNull ComparablePair other) {
            return (int)(first - other.first);
        }
    }
}