package com.fsck.k9.fragment;


import java.util.List;

import android.text.TextUtils;

import com.fsck.k9.Account;
import com.fsck.k9.DI;
import com.fsck.k9.Preferences;
import com.fsck.k9.controller.MessageReference;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalStore;
import com.fsck.k9.mailstore.LocalStoreProvider;


public class MlfUtils {

    static LocalFolder getOpenFolder(long folderId, Account account) throws MessagingException {
        LocalStore localStore = DI.get(LocalStoreProvider.class).getInstance(account);
        LocalFolder localFolder = localStore.getFolder(folderId);
        localFolder.open();
        return localFolder;
    }

    static void setLastSelectedFolder(Preferences preferences, List<MessageReference> messages, long folderId) {
        MessageReference firstMsg = messages.get(0);
        Account account = preferences.getAccount(firstMsg.getAccountUuid());
        account.setLastSelectedFolderId(folderId);
    }

    static String buildSubject(String subjectFromCursor, String emptySubject, int threadCount) {
        if (TextUtils.isEmpty(subjectFromCursor)) {
            return emptySubject;
        } else if (threadCount > 1) {
            // If this is a thread, strip the RE/FW from the subject.  "Be like Outlook."
            return Utility.stripSubject(subjectFromCursor);
        }
        return subjectFromCursor;
    }
}
