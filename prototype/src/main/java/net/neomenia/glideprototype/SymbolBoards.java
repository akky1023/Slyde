package net.neomenia.glideprototype;

import android.content.SharedPreferences;

final class SymbolBoards {
    private static final String[][] DEFAULTS={
        {"@","#","/","!","?","_","(",")",":","-",",","."},
        {"'","\"","\\","+","*","=","[","]",";","<",">","%"}
    };
    static String[] defaults(int board){return DEFAULTS[board].clone();}
    static String[] load(SharedPreferences prefs,int board){
        String[] values=defaults(board);
        for(int i=0;i<values.length;i++)values[i]=prefs.getString("symbol."+board+"."+i,values[i]);
        return values;
    }
    static boolean returns(SharedPreferences prefs,int board){return prefs.getBoolean("symbolReturn."+board,true);}
    static void save(SharedPreferences prefs,int board,String[] values,boolean returnAfterInput){
        SharedPreferences.Editor edit=prefs.edit().putBoolean("symbolReturn."+board,returnAfterInput);
        for(int i=0;i<values.length;i++)edit.putString("symbol."+board+"."+i,values[i]);
        edit.apply();
    }
}
