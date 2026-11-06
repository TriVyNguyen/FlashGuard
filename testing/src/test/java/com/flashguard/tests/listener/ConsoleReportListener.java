package com.flashguard.tests.listener;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/** In kết quả từng test ra console cho dễ theo dõi khi demo. */
public class ConsoleReportListener implements ITestListener {

    @Override
    public void onTestSuccess(ITestResult r) {
        print("PASS", r);
    }

    @Override
    public void onTestFailure(ITestResult r) {
        print("FAIL", r);
        if (r.getThrowable() != null) {
            System.out.println("       └─ " + r.getThrowable().getMessage());
        }
    }

    @Override
    public void onTestSkipped(ITestResult r) {
        print("SKIP", r);
    }

    @Override
    public void onFinish(ITestContext ctx) {
        System.out.printf("[%s] passed=%d, failed=%d, skipped=%d%n", ctx.getName(),
                ctx.getPassedTests().size(), ctx.getFailedTests().size(), ctx.getSkippedTests().size());
    }

    private static void print(String status, ITestResult r) {
        String desc = r.getMethod().getDescription();
        long ms = r.getEndMillis() - r.getStartMillis();
        System.out.printf("  %s  %s.%s (%d ms)%s%n", status, r.getTestClass().getRealClass().getSimpleName(),
                r.getMethod().getMethodName(), ms, desc == null || desc.isBlank() ? "" : " — " + desc);
    }
}
