package kr.blogspot.charlie0301.wimple.impl;

final class WimpleApiQuota {

    private int remaining = -1;
    private int total = 1;

    int getRemaining() {
        return remaining;
    }

    int getTotal() {
        return total;
    }

    void setTotalByApiCountLevel(int level) {
        switch (level) {
            case 2:
                total = 200;
                break;
            case 3:
                total = 1000;
                break;
            default:
                total = 30;
                break;
        }
    }

    void setRemaining(String count) {
        remaining = Integer.parseInt(count);
    }
}
