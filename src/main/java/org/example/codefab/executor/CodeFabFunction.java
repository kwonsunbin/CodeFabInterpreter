package org.example.codefab.executor;

import org.example.codefab.ast.Stmt;

import java.util.List;

/** Runtime representation of a user-defined function with a captured closure. */
public class CodeFabFunction implements CodeFabCallable {

    public final Stmt.Function declaration;
    private final Environment closure;

    public CodeFabFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Executor executor, List<Object> arguments) {
        Environment funcEnv = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            funcEnv.define(declaration.params.get(i).origin(), arguments.get(i));
        }
        try {
            executor.executeBlock(declaration.body, funcEnv);
        } catch (ReturnException ret) {
            return ret.value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "<function " + declaration.name.origin() + ">";
    }
}
