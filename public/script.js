let isAcknowledged = false; // Флаг, показывающий, ознакомлен ли пользователь

function acknowledge() {
    isAcknowledged = true;
    const acknowledgeButton = document.querySelector('.table-footer button:last-child');
    acknowledgeButton.classList.add('acknowledged'); // Добавляем класс для отображения галочки
    alert('Вы ознакомлены с данными!');
}

// Добавляем обработчик события на кнопку "Ознакомлен"
const acknowledgeButton = document.querySelector('.table-footer button:last-child');
acknowledgeButton.addEventListener('click', acknowledge);

// Функция для удаления галочки при обновлении данных
function resetAcknowledge() {
    isAcknowledged = false;
    const acknowledgeButton = document.querySelector('.table-footer button:last-child');
    acknowledgeButton.classList.remove('acknowledged');
}

fetch('http://192.168.1.1:3000/issues') // Замените на URL вашего API
    .then(response => response.json())
    .then(data => {
        resetAcknowledge(); // Сбрасываем состояние ознакомления при обновлении данных

        // Обработка полученных данных
        const tableBody = document.querySelector('#tableBody');
        tableBody.innerHTML = ''; // Очищаем таблицу перед добавлением новых данных

        data.forEach(issue => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${issue.deviceSerialNumber}</td>
                <td>${issue.location}</td>
                <td data-status="${getStatusClass(issue.issueType)}">
                    <span class="status-indicator ${getStatusClass(issue.issueType)}"></span>
                    ${issue.photoUrl ? `<img src="${issue.photoUrl}" alt="Фото" class="claim-photo">` : ''}
                    <p>${issue.description}</p>
                </td>
            `;
            tableBody.appendChild(row);
        });
    })
    .catch(error => console.error('Ошибка:', error));

function getStatusClass(issueType) {
    switch (issueType) {
        case 'Нет неисправности':
            return 'green';
        case 'Неисправность':
            return 'yellow';
        case 'Экстренная неисправность':
            return 'orange';
        default:
            return 'green'; // Или другой цвет по умолчанию
    }
}
