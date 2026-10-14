package com.campus.lostfound.entity;

/**
 * 招领启事的保管方式。
 */
public enum Custody {
    /** 拾得者自留，走站内会话约定交还 */
    SELF,
    /** 已移交保卫处，失主凭移交单号线下领取 */
    AT_SECURITY
}
