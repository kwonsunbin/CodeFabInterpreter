package org.example.codefab.executor;

import org.example.codefab.ast.Stmt;

import java.util.List;

/**
 * 사용자 정의 함수의 런타임 표현. 선언 시점의 환경(클로저)을 캡처하므로,
 * 함수 안에서 바깥 변수와 자기 자신(재귀)을 참조할 수 있다.
 */
public class CodeFabFunction implements CodeFabCallable {

    private final Stmt.Function declaration;
    private final Environment   closure;

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
        // 클로저를 부모로 하는 호출 프레임을 만들고 매개변수를 바인딩한다.
        Environment frame = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            frame.define(declaration.params.get(i).origin(), arguments.get(i));
        }
        try {
            executor.executeBlock(declaration.body, frame);
        } catch (ReturnException ret) {
            return ret.value; // return 문이 던진 값
        }
        return null; // 반환문 없이 끝나면 nil
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.origin() + ">";
    }
}
