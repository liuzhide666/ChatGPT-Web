package com.liuchenlin.chatgptweb;

/** Historical guard: the old age-based PageHealthPolicy must never re-enter production. */
public final class PageHealthPolicyTest {
    public static void main(String[] args) {
        try {
            Class.forName("com.liuchenlin.chatgptweb.PageHealthPolicy");
            throw new AssertionError("legacy time-stale PageHealthPolicy must be absent in vc113");
        } catch (ClassNotFoundException expected) {
            System.out.println("PageHealthPolicyTest OK");
        }
    }
}
