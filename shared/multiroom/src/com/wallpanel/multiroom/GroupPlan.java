package com.wallpanel.multiroom;
import java.util.*;
/** Bounds shared by discovered identities, presets and live membership changes. */
public final class GroupPlan {
 public static boolean validId(String value){return value!=null&&value.matches("panel-[A-Za-z0-9-]{8,80}");}
 public static String label(String value){String text=value.trim();if(text.isEmpty()||text.length()>64||text.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Use a room/group name of 1 to 64 characters");return text;}
 public static List<String> members(Collection<String> input,String leader){
  if(input.size()>32||!validId(leader))throw new IllegalArgumentException("Choose up to 32 rooms");LinkedHashSet<String> ids=new LinkedHashSet<>();ids.add(leader);for(String key:input){if(!validId(key))throw new IllegalArgumentException("Invalid room");ids.add(key);}if(ids.size()>32)throw new IllegalArgumentException("Choose up to 32 rooms");return new ArrayList<>(ids);
 }
 private GroupPlan(){}
}
