package net.neomenia.glideprototype;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;

/** Device regression tests using the same session and Android's real composing spans. */
public final class SessionInstrumentation extends Instrumentation {
    private static final class Connection extends BaseInputConnection {
        final Editable document=new SpannableStringBuilder();
        int action=-1;
        Connection(Context context){super(new View(context),true);Selection.setSelection(document,0);}
        @Override public Editable getEditable(){return document;}
        @Override public boolean performEditorAction(int value){action=value;return true;}
    }
    private static void equal(Object expected,Object actual){if(!expected.equals(actual))throw new AssertionError("expected="+expected+", actual="+actual);}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            runOnMainSync(()->{
                Context context=new ContextThemeWrapper(getTargetContext(),android.R.style.Theme_Material_Light_NoActionBar);
                DockChecks.run(context);
                KeyboardSession session=new KeyboardSession(context,null,null);
                EditorInfo info=new EditorInfo();info.inputType=0x20001;info.imeOptions=EditorInfo.IME_ACTION_NONE;
                try {
                    Connection c=new Connection(context);session.start(c,info);
                    session.text("n",true);session.text("n",true);equal("ん",c.document.toString());session.text("a",true);equal("んあ",c.document.toString());session.action("enter");c.document.clear();Selection.setSelection(c.document,0);
                    session.text("nihongo",true);equal("にほんご",c.document.toString());session.action("enter");
                    equal(-1,BaseInputConnection.getComposingSpanStart(c.document));session.action("enter");equal("にほんご\n",c.document.toString());
                    session.text("😀",false);session.action("delete");equal("にほんご\n",c.document.toString());
                    Selection.setSelection(c.document,0,4);session.action("delete");equal("\n",c.document.toString());
                    Selection.setSelection(c.document,c.document.length());session.text("n",true);session.finish();equal("\nん",c.document.toString());
                    Connection next=new Connection(context);info.imeOptions=EditorInfo.IME_ACTION_SEARCH;session.start(next,info);session.action("enter");equal(EditorInfo.IME_ACTION_SEARCH,next.action);
                    session.text("ka",true);Selection.setSelection(next.document,0);session.selectionChanged(0,0,1);session.text("ki",true);session.action("enter");equal("きか",next.document.toString());
                    session.finish();Connection password=new Connection(context);info.inputType=0x81;session.start(password,info);session.text("password",true);equal("password",password.document.toString());equal(-1,BaseInputConnection.getComposingSpanStart(password.document));
                    session.finish();session.text("ignored",false);equal("password",password.document.toString());
                } finally {session.destroy();}
            });
            result.putString("stream","InputConnection regression checks passed\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream",error.toString());finish(Activity.RESULT_CANCELED,result);}
    }
}
