package io.sketch.mochaagents.agents;

public class ActionOutput {
    
    private final Object output;
    private final boolean isFinal;
    
    public ActionOutput(Object output, boolean isFinal) {
        this.output = output;
        this.isFinal = isFinal;
    }
    
    public Object output() {
        return output;
    }
    
    public boolean isFinal() {
        return isFinal;
    }
}