$(document).ready(function () {
    // Scroll到底部自動勾選checkbox、button可用
    $(".content").scroll(function () {
        maxScrollHeight = $(".content").prop("scrollHeight") - $(".content").height() - 0;
        if (maxScrollHeight - $(this).scrollTop() <= 50) {
            $("input#checkbox").prop("checked", true);
            $(".btn.disabled").removeClass("disabled");
        }
    });
    // 手動勾選checkbox，button可用
    $("input#checkbox").click(function () {
        if ($(this).is(":checked")) {
            $(".btn.disabled").removeClass("disabled");
        } else {
            $(".btn").addClass("disabled");
        }
    });
});