const fs = require('fs');

const filepath = 'P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/listing/AiListingAssistant.tsx';
let content = fs.readFileSync(filepath, 'utf-8');

const regex1 = /const handleFinalizeChat = async \(\) => {[\s\S]*?setIsFinalizing\(false\);\s*}/;

const new1 = `const handleFinalizeChat = async () => {
    setIsFinalizing(true);
    setErrorMsg(null);
    try {
      const res = await postService.finalizeChat(sessionId);
      setIsFinalized(true);
      setFinalizeNotice(
        typeof res === 'string' && res.length > 0 && !res.toLowerCase().includes('finalized')
          ? res
          : (res?.description || (lang === 'vi'
              ? 'AI đã tổng hợp cuộc trò chuyện và cập nhật mô tả chi tiết vào bài đăng thành công! Bạn có thể gửi bài đăng ngay bây giờ.'
              : 'AI has summarized the conversation and saved the description to your post! You can now submit the post.'))
      );
    } catch (err: any) {
      setErrorMsg(err?.message || (lang === 'vi' ? 'Lỗi khi hoàn tất mô tả AI. Vui lòng thử lại.' : 'Failed to finalize AI description.'));
    } finally {
      setIsFinalizing(false);
    }
  };

  const handleRegenerateDescription = async () => {
    setIsFinalizing(true);
    setIsFinalized(false);
    setFinalizeNotice(null);
    try {
      const res = await aiChatService.regenerate(sessionId);
      setIsFinalized(true);
      setFinalizeNotice(
        typeof res === 'string' && res.length > 0 && !res.toLowerCase().includes('finalized')
          ? res
          : (res?.description || (lang === 'vi'
              ? 'AI đã tạo lại mô tả thành công!'
              : 'AI has regenerated the description successfully!'))
      );
    } catch (err: any) {
      setErrorMsg(err?.message || (lang === 'vi' ? 'Không thể tạo lại mô tả.' : 'Failed to regenerate description.'));
    } finally {
      setIsFinalizing(false);
    }
  }`;

content = content.replace(regex1, new1);

const regex2 = /\{!isFinalized \? \([\s\S]*?\) : \(\s*<button\s*type="button"\s*onClick=\{handleSubmitPost\}/;

const new2 = `{!isFinalized ? (
              <button
                type="button"
                onClick={handleFinalizeChat}
                disabled={isFinalizing || isSending}
                className="px-4 py-2 rounded-xl bg-[#24263e] hover:bg-black text-white text-xs font-black shadow-sm flex items-center gap-1.5 cursor-pointer disabled:opacity-50 transition"
              >
                {isFinalizing ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    <span>{lang === 'vi' ? 'AI đang tạo mô tả...' : 'Compiling description...'}</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>{lang === 'vi' ? 'Hoàn tất mô tả sản phẩm' : 'Finalize Description'}</span>
                  </>
                )}
              </button>
            ) : (
              <>
                <button
                  type="button"
                  onClick={handleRegenerateDescription}
                  disabled={isFinalizing}
                  className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-600 text-white text-xs font-bold shadow-sm flex items-center gap-1.5 cursor-pointer disabled:opacity-50 transition"
                >
                  {isFinalizing ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <Bot className="w-3.5 h-3.5" />
                  )}
                  <span>{lang === 'vi' ? 'Tạo lại' : 'Regen'}</span>
                </button>
                <button
                  type="button"
                  onClick={handleSubmitPost}`;

content = content.replace(regex2, new2);
fs.writeFileSync(filepath, content);
console.log('AiListingAssistant patched');
