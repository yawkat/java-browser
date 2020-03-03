"use strict";

class Dialog {
    constructor(wrapperElement) {
        this.wrapperElement = wrapperElement;

        let ref = this;

        // if we click on the wrapper itself (i.e. the greyed-out background), close this dialog
        wrapperElement.addEventListener('click', function (evt) {
            if (evt.target === wrapperElement) {
                ref.close();
            }
        });
    }

    close() {
        this.wrapperElement.classList.remove("dialog-visible");
        Dialog.currentDialog = null;
    }

    open() {
        if (Dialog.currentDialog !== this) {
            if (Dialog.currentDialog !== null) {
                Dialog.currentDialog.close();
            }
            Dialog.currentDialog = this;
            this.wrapperElement.classList.add("dialog-visible");
        }
    }
}

Dialog.currentDialog = null;

$(function () {
    document.addEventListener('keydown', function (e) {
        if (e.key === "Escape" && Dialog.currentDialog !== null) {
            Dialog.currentDialog.close();
        }
    });

    $(".size-expander-expand").click(function () {
        $(this).closest(".size-expander-wrapper").removeClass("retracted");
    });

    $("#theme-selector").change(function () {
        const newTheme = $(this).val();
        const newThemeClass = "theme-" + newTheme;
        const html = $("html");
        html.addClass(newThemeClass);
        const classes = html.attr("class").split(/\s+/);
        for (const clazz of classes) {
            if (clazz.indexOf("theme-") === 0 && clazz !== newThemeClass) {
                html.removeClass(clazz);
            }
        }

        document.cookie = "theme=" + newTheme + "; expires=Fri, 1 Jan 9999 00:00:00 UTC; path=/";
    });
});

function expandDeclaration(element) {
    const wrapped = $(element);
    const container = wrapped.closest("li");
    const loadChildrenFrom = wrapped.data('load-children-from');
    if (loadChildrenFrom) {
        wrapped.data('loadChildrenFrom', null);
        const spinner = $("<span class='spinner'>…</span>");
        container.append(spinner);
        $.ajax({
            url: loadChildrenFrom,
            dataType: 'html',
            success: function (html) {
                spinner.remove();
                container.append(html);
            }
        });
    }
    container.toggleClass('expanded');
}