import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {NotificationsService, SimpleNotificationsComponent} from 'angular2-notifications/components';
import {AdminComponent} from '../../common/admin.component';
import {SecurityService} from '../../../service/security.service';
import {NotificationService} from '../../../service/notification.service';

@Component({
  selector: 'profile',
  template: require('./profile.pug'),
  directives: [SimpleNotificationsComponent],
  providers: [NotificationsService, NotificationService]
})
export class AdminProfile extends AdminComponent {
    notificationsOptions = {};

    constructor(private service: SecurityService,
                private notifyService: NotificationService,
                router: Router,
                securityService: SecurityService) {
      super(router, securityService);
    }

    removeTokens() {
        this.service.deleteTokens().subscribe(() => {
            this.notifyService.info('Done', 'Token removed.');
        }, response => this.notifyService.error('Error', 'Can\'t delete tokens.'));
    }
}
