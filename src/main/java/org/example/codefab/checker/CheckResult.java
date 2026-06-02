package org.example.codefab.checker;

import java.util.ArrayList;
import java.util.List;

public class CheckResult {

    public final List<Diagnostic> errors   = new ArrayList<>();
    public final List<Diagnostic> warnings = new ArrayList<>();

    public boolean ok() { return errors.isEmpty(); }

    public void addError(int line, String message) {
        errors.add(new Diagnostic(Diagnostic.Severity.ERROR, line, message));
    }

    public void addWarning(int line, String message) {
        warnings.add(new Diagnostic(Diagnostic.Severity.WARNING, line, message));
    }
}
