package dev.slidev.intellij.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded, cancelable compiler subprocess, independent of IntelliJ APIs. */
public final class ExpressionChecker {
    public record Diagnostic(int start,int end,String message) {}
    public record Result(List<Diagnostic> diagnostics,String unavailable) {}
    private static final String PREFIX="slidev-expression\t";
    public static Result check(Path entry,String template,String node,String script,Runnable canceled) {
        Path input=null,output=null;Process child=null;
        try {
            canceled.run();
            if(template.length()>500_000) return new Result(List.of(),"Live expression check skipped: document exceeds 500,000 characters.");
            input=Files.createTempFile("slidev-expression-", ".txt");
            output=Files.createTempFile("slidev-expression-", ".log");
            Files.writeString(input,template,StandardCharsets.UTF_8);
            child=new ProcessBuilder(node,"-e",script,entry.toAbsolutePath().toString())
                .directory(entry.toAbsolutePath().getParent().toFile())
                .redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!child.waitFor(50,TimeUnit.MILLISECONDS)) {
                canceled.run();
                if(System.nanoTime()>deadline) return new Result(List.of(),"Live expression check timed out after 5 seconds.");
                if(Files.size(output)>2_000_000) return new Result(List.of(),"Live expression checker output limit exceeded.");
            }
            canceled.run();
            if(Files.size(output)>2_000_000) return new Result(List.of(),"Live expression checker output limit exceeded.");
            if(child.exitValue()!=0) return new Result(List.of(),"Node expression checker exited with code "+child.exitValue()+". Check the Node.js executable in Settings > Slidev.");
            return parse(Files.readString(output,StandardCharsets.UTF_8),template.length());
        } catch(IOException ex) {
            return new Result(List.of(),"Live expression check unavailable: "+ex.getMessage());
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();return new Result(List.of(),"Live expression check interrupted.");
        } finally {
            if(child!=null && child.isAlive()) {
                child.destroyForcibly();
                try {child.waitFor(1,TimeUnit.SECONDS);} catch(InterruptedException ex) {Thread.currentThread().interrupt();}
            }
            for(Path file:new Path[]{input,output}) if(file!=null) try {Files.deleteIfExists(file);} catch(IOException ignored) {}
        }
    }
    public static Result parse(String output,int sourceLength) {
        List<Diagnostic> diagnostics=new ArrayList<>();boolean ready=false;
        for(String line:output.split("\n")) {
            if(!line.startsWith(PREFIX)) continue;
            String[] fields=line.stripTrailing().substring(PREFIX.length()).split("\t",-1);
            try {
                if(fields[0].equals("ready") && fields.length==1) ready=true;
                else if(fields[0].equals("unavailable") && fields.length==2) return new Result(List.of(),decode(fields[1]));
                else if(fields[0].equals("error") && fields.length==4 && diagnostics.size()<200) {
                    int start=Integer.parseInt(fields[1]),end=Integer.parseInt(fields[2]);
                    if(start>=0 && start<=end && end<=sourceLength) diagnostics.add(new Diagnostic(start,end,decode(fields[3])));
                    else return new Result(List.of(),"Invalid expression diagnostic range.");
                } else return new Result(List.of(),"Invalid expression checker response.");
            } catch(IllegalArgumentException ex) {return new Result(List.of(),"Invalid expression checker response.");}
        }
        return ready?new Result(List.copyOf(diagnostics),null):new Result(List.of(),"No response from the live expression checker.");
    }
    private static String decode(String value) {return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private ExpressionChecker() {}
}
