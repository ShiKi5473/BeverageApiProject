import { createQuantitySelector } from "./QuantitySelector.js";

/**
 * 建立客製化選項 Modal 的 "內容"
 * @param {object} product - 商品物件 (來自 API)
 * @returns { {element: HTMLElement, getSelectedData: () => object} }
 */
export function createOptionsModalContent(product) {
  // 1. 建立根元素
  const modalContent = document.createElement("div");

  // 2. 建立標題
  const title = document.createElement("h3");
  title.textContent = product.name;
  modalContent.appendChild(title);

  // 3. 遍歷 OptionGroups
  product.optionGroups.forEach((group) => {
    const groupContainer = document.createElement("div");
    groupContainer.className = "option-group";

    const groupTitle = document.createElement("h4");
    const selectionType = group.selectionType === "SINGLE" ? "單選" : "多選";
    groupTitle.textContent = `${group.name} (${selectionType})`;
    groupContainer.appendChild(groupTitle);

    const buttonsContainer = document.createElement("div");
    buttonsContainer.className = "option-buttons-container";

    group.options.forEach((option) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "option-btn";
      let labelText = option.optionName;
      if (option.priceAdjustment > 0) {
        labelText += ` (+NT$ ${option.priceAdjustment})`;
      }
      button.textContent = labelText;
      button.dataset.optionId = option.optionId;
      buttonsContainer.appendChild(button);
    });

    buttonsContainer.addEventListener("click", (event) => {
      const clickedButton = event.target.closest(".option-btn");
      if (!clickedButton) return;
      if (group.selectionType === "SINGLE") {
        buttonsContainer
          .querySelectorAll(".option-btn")
          .forEach((btn) => btn.classList.remove("active"));
        clickedButton.classList.add("active");
      } else {
        clickedButton.classList.toggle("active");
      }
    });

    groupContainer.appendChild(buttonsContainer);
    modalContent.appendChild(groupContainer);
  });

  // 4. 建立數量選擇器
  const quantitySelector = createQuantitySelector(1);
  modalContent.appendChild(quantitySelector.element);

  // 5. 建立備註
  const notesInput = document.createElement("input");
  notesInput.type = "text";
  notesInput.placeholder = "備註...";
  notesInput.className = "modal-notes";
  modalContent.appendChild(notesInput);

  // 6.
  const getSelectedData = () => {
    const selectedOptionIds = Array.from(
      modalContent.querySelectorAll(".option-btn.active")
    ).map((btn) => btn.dataset.optionId);

    return {
      quantity: quantitySelector.getQuantity(),
      notes: notesInput.value,
      selectedOptionIds: selectedOptionIds,
    };
  };

  return {
    element: modalContent,
    getSelectedData: getSelectedData,
  };
}
