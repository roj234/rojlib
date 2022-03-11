package roj.net.mychat;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/3/11 20:55
 */
public interface ChatDAO {
    Result login(String user, String pass);
    Result register(String user, String pass);
    Result changePassword(int user, String oldPass, String newPass);
    Result setUserState(int user, int state);

    User obtainUserData(int uid);
    Result setUserData(User user);
    Group obtainGroupData(int uid);
    Result setGroupData(Group group);

    // 群聊的历史纪录只在内存中保存
    Integer getHistoryCount(int uid);
    List<Message> getHistory(int uid, int off, int len);
    Result addHistory(int uid, Message msg);
    Result delHistory(int uid);
    Result delHistory(int uid, int targetId);

    Integer getSpaceCount();
    Integer getSpaceCount(int uid);
    List<SpaceEntry> getSpace(Integer uid, int off, int len);
    Result addSpace(int uid, CharSequence msg);

    final class Result {
        public String error;
        public int uid;

        public static Result err(String err) {
            Result r = new Result();
            r.error = err;
            return r;
        }

        public static Result suc(int uid) {
            Result r = new Result();
            r.uid = uid;
            return r;
        }
    }
}
