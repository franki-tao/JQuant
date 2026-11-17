package jquant.cashflows;

//! Duration type
/*! \ingroup cashflows */
public class Duration {
    private Duration() {
    }

    public enum Type {
        Simple,
        Macaulay,
        Modified
    }
}
