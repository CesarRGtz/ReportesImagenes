package com.teosa.app.prototipo.presentation;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/** Keeps the reading position in pixels even if a preview gains or loses pages. */
final class ViewportPosition {
    private final ScrollPane pane;
    private final double x,y;
    private ViewportPosition(ScrollPane pane){
        this.pane=pane;
        x=pane==null?0:offset(pane.getHvalue(),pane.getHmin(),pane.getHmax(),width());
        y=pane==null?0:offset(pane.getVvalue(),pane.getVmin(),pane.getVmax(),height());
    }
    static ViewportPosition capture(ScrollPane pane){return new ViewportPosition(pane);}
    static ViewportPosition captureAncestor(Node node){
        for(Node parent=node.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollPane scroll)return capture(scroll);
        return capture(null);
    }
    private double width(){return Math.max(0,pane.getContent().getLayoutBounds().getWidth()-pane.getViewportBounds().getWidth());}
    private double height(){return Math.max(0,pane.getContent().getLayoutBounds().getHeight()-pane.getViewportBounds().getHeight());}
    private static double offset(double value,double min,double max,double extent){return max==min?0:(value-min)/(max-min)*extent;}
    private static double value(double offset,double extent,double min,double max){return extent<=0?min:min+Math.min(1,offset/extent)*(max-min);}
    void restore(){
        if(pane==null||pane.getContent()==null)return;
        // Complete layout before restoring: otherwise ScrollPane clamps to the old extent.
        pane.applyCss();pane.layout();
        pane.setHvalue(value(x,width(),pane.getHmin(),pane.getHmax()));
        pane.setVvalue(value(y,height(),pane.getVmin(),pane.getVmax()));
    }
}
